const mysql = require('mysql2/promise');
const fs = require('fs');
const path = require('path');

const TIDB_CONFIG = {
  host: process.env.TIDB_HOST || 'gateway01.ap-southeast-1.prod.aws.tidbcloud.com',
  port: parseInt(process.env.TIDB_PORT || '4000', 10),
  user: process.env.TIDB_USER || 'Hs83Gackekaey61.root',
  password: process.env.TIDB_PASSWORD || 'WQLhO9SyMi1x8YPe',
  database: process.env.TIDB_DATABASE || 'itinventory',
  ssl: {
    minVersion: 'TLSv1.2',
    rejectUnauthorized: true
  }
};

async function initTiDBSchema() {
  console.log(`[TiDB] Connecting to ${TIDB_CONFIG.host}:${TIDB_CONFIG.port}...`);
  
  // First connect to default sys/mysql database to check/create itinventory database
  let initialConn;
  try {
    initialConn = await mysql.createConnection({
      ...TIDB_CONFIG,
      database: 'sys'
    });
    console.log('[TiDB] Connected to cluster. Creating database if not exists...');
    await initialConn.query(`CREATE DATABASE IF NOT EXISTS \`${TIDB_CONFIG.database}\`;`);
    console.log(`[TiDB] Database \`${TIDB_CONFIG.database}\` confirmed ready.`);
    await initialConn.end();
  } catch (err) {
    console.warn('[TiDB] Could not create database directly, will use default database:', err.message);
    if (initialConn) await initialConn.end();
  }

  // Connect to target database
  const connection = await mysql.createConnection(TIDB_CONFIG);
  console.log(`[TiDB] Connected to target database \`${TIDB_CONFIG.database}\``);

  // Table 1: users
  await connection.query(`
    CREATE TABLE IF NOT EXISTS users (
      user_id VARCHAR(50) PRIMARY KEY,
      full_name VARCHAR(100) NOT NULL,
      email VARCHAR(100) UNIQUE NOT NULL,
      password VARCHAR(255) NOT NULL,
      role VARCHAR(20) DEFAULT 'scanner',
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );
  `);
  console.log('[TiDB] Table `users` checked/created.');

  // Table 2: equipment_items
  await connection.query(`
    CREATE TABLE IF NOT EXISTS equipment_items (
      item_id VARCHAR(50) PRIMARY KEY,
      asset_tag VARCHAR(50) UNIQUE NOT NULL,
      item_name VARCHAR(150) NOT NULL,
      category VARCHAR(50) NOT NULL,
      serial_number VARCHAR(100),
      status ENUM('Available', 'Assigned', 'Maintenance', 'Defective') DEFAULT 'Available',
      assigned_to VARCHAR(100),
      location VARCHAR(150) NOT NULL,
      last_scanned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
      last_scanned_by VARCHAR(100),
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );
  `);
  console.log('[TiDB] Table `equipment_items` checked/created.');

  // Table 3: scan_logs
  await connection.query(`
    CREATE TABLE IF NOT EXISTS scan_logs (
      log_id VARCHAR(50) PRIMARY KEY,
      asset_tag VARCHAR(50) NOT NULL,
      item_name VARCHAR(150) NOT NULL,
      action_type VARCHAR(50) NOT NULL,
      scanned_by VARCHAR(100) NOT NULL,
      location VARCHAR(150),
      remarks TEXT,
      timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );
  `);
  console.log('[TiDB] Table `scan_logs` checked/created.');

  // Seed default admin and scanner users
  await connection.query(`
    INSERT INTO users (user_id, full_name, email, password, role)
    VALUES 
      ('usr_admin_01', 'Admin Staff', 'admin@itinventory.local', 'admin123', 'admin'),
      ('usr_scanner_01', 'John Scanner', 'scanner@itinventory.local', 'scanner123', 'scanner')
    ON DUPLICATE KEY UPDATE full_name = VALUES(full_name);
  `);
  console.log('[TiDB] Default users seeded.');

  // Check if equipment table is empty, if so, load from seed file
  const [items] = await connection.query('SELECT COUNT(*) as cnt FROM equipment_items;');
  if (items[0].cnt === 0) {
    const seedPath = path.join(__dirname, 'firestore-seed.json');
    if (fs.existsSync(seedPath)) {
      const seedData = JSON.parse(fs.readFileSync(seedPath, 'utf-8'));
      if (seedData.equipment_items && seedData.equipment_items.length > 0) {
        for (const item of seedData.equipment_items) {
          await connection.query(`
            INSERT INTO equipment_items 
              (item_id, asset_tag, item_name, category, serial_number, status, assigned_to, location, last_scanned_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE item_name = VALUES(item_name);
          `, [
            item.item_id,
            item.asset_tag,
            item.item_name,
            item.category || 'Laptop',
            item.serial_number || 'N/A',
            item.status || 'Available',
            item.assigned_to || '',
            item.location || 'Computer Laboratory 1',
            item.last_scanned_by || 'Admin'
          ]);
        }
        console.log(`[TiDB] Seeded ${seedData.equipment_items.length} equipment items.`);
      }

      if (seedData.scan_logs && seedData.scan_logs.length > 0) {
        for (const log of seedData.scan_logs) {
          await connection.query(`
            INSERT INTO scan_logs 
              (log_id, asset_tag, item_name, action_type, scanned_by, location, remarks)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE item_name = VALUES(item_name);
          `, [
            log.log_id,
            log.asset_tag,
            log.item_name,
            log.action_type || 'Audit Verify',
            log.scanned_by || 'Staff',
            log.location || 'Computer Laboratory 1',
            log.remarks || ''
          ]);
        }
        console.log(`[TiDB] Seeded ${seedData.scan_logs.length} scan audit logs.`);
      }
    }
  }

  const [countRes] = await connection.query('SELECT COUNT(*) as eq_count FROM equipment_items;');
  const [logCountRes] = await connection.query('SELECT COUNT(*) as log_count FROM scan_logs;');
  console.log(`[TiDB] Initialized! Total Equipment: ${countRes[0].eq_count}, Total Scan Logs: ${logCountRes[0].log_count}`);

  await connection.end();
}

if (require.main === module) {
  initTiDBSchema()
    .then(() => process.exit(0))
    .catch(err => {
      console.error('[TiDB] Setup failed:', err);
      process.exit(1);
    });
}

module.exports = { initTiDBSchema, TIDB_CONFIG };

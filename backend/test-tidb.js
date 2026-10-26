const mysql = require('mysql2/promise');

async function testConnection() {
  console.log('[TiDB] Connecting to gateway01.ap-southeast-1.prod.aws.tidbcloud.com:4000...');
  try {
    const connection = await mysql.createConnection({
      host: 'gateway01.ap-southeast-1.prod.aws.tidbcloud.com',
      port: 4000,
      user: 'Hs83Gackekaey61.root',
      password: 'WQLhO9SyMi1x8YPe',
      database: 'sys',
      ssl: {
        minVersion: 'TLSv1.2',
        rejectUnauthorized: true
      }
    });

    console.log('[TiDB] Connection SUCCESSFUL!');
    const [rows] = await connection.query('SELECT VERSION() as version, DATABASE() as current_db;');
    console.log('[TiDB] Connected Version & DB:', rows);

    // Check if we can create or use a dedicated database or tables in sys
    const [databases] = await connection.query('SHOW DATABASES;');
    console.log('[TiDB] Databases:', databases.map(d => Object.values(d)[0]));

    await connection.end();
  } catch (err) {
    console.error('[TiDB] Connection failed:', err);
  }
}

testConnection();

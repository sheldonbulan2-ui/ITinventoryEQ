const mysql = require('mysql2/promise');

async function updateOfficialAccount() {
  const connection = await mysql.createConnection({
    host: 'gateway01.ap-southeast-1.prod.aws.tidbcloud.com',
    port: 4000,
    user: 'Hs83Gackekaey61.root',
    password: 'WQLhO9SyMi1x8YPe',
    database: 'itinventory',
    ssl: { minVersion: 'TLSv1.2', rejectUnauthorized: true }
  });

  // Remove old demo accounts
  await connection.query("DELETE FROM users WHERE email IN ('admin@itinventory.local', 'admin@itinventory.com');");

  // Insert or update official admin account
  await connection.query(`
    INSERT INTO users (user_id, full_name, email, password, role)
    VALUES ('usr_admin_official', 'System Administrator', 'admin@itinventory.ph', 'Admin@IT2026!', 'admin')
    ON DUPLICATE KEY UPDATE 
      password = 'Admin@IT2026!', 
      full_name = 'System Administrator',
      email = 'admin@itinventory.ph';
  `);

  console.log('[TiDB] Official Admin Account created:');
  console.log('Username / Email: admin@itinventory.ph');
  console.log('Password: Admin@IT2026!');

  const [users] = await connection.query('SELECT user_id, full_name, email, role FROM users;');
  console.log('[TiDB] Active Users:', users);

  await connection.end();
}

updateOfficialAccount().catch(console.error);

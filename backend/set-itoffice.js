const mysql = require('mysql2/promise');

async function setITofficeCredentials() {
  const connection = await mysql.createConnection({
    host: 'gateway01.ap-southeast-1.prod.aws.tidbcloud.com',
    port: 4000,
    user: 'Hs83Gackekaey61.root',
    password: 'WQLhO9SyMi1x8YPe',
    database: 'itinventory',
    ssl: { minVersion: 'TLSv1.2', rejectUnauthorized: true }
  });

  // Delete older admin users
  await connection.query("DELETE FROM users WHERE email IN ('admin@itinventory.ph', 'admin@itinventory.local', 'admin@itinventory.com');");

  // Insert or update official IToffice credentials
  await connection.query(`
    INSERT INTO users (user_id, full_name, email, password, role)
    VALUES ('usr_it_office', 'IT Office Administrator', 'IToffice', 'IToffice', 'admin')
    ON DUPLICATE KEY UPDATE 
      password = 'IToffice', 
      full_name = 'IT Office Administrator',
      email = 'IToffice';
  `);

  console.log('[TiDB] Successfully set credentials:');
  console.log('Username: IToffice');
  console.log('Password: IToffice');

  const [users] = await connection.query('SELECT user_id, full_name, email, role FROM users;');
  console.log('[TiDB] Active Users:', users);

  await connection.end();
}

setITofficeCredentials().catch(console.error);

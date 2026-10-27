/**
 * IT Equipment Inventory System - Production Backend API Server
 * Synchronized with TiDB Cloud Serverless MySQL Database.
 * Features Real-time Server-Sent Events (SSE) for instant Web Admin Panel sync.
 * Ready for Render.com deployment.
 */

require('dotenv').config();
const http = require('http');
const fs = require('fs');
const path = require('path');
const url = require('url');
const mysql = require('mysql2/promise');
const { initTiDBSchema, TIDB_CONFIG } = require('./init-tidb-schema');

const PORT = process.env.PORT || 5000;
const WEB_DIR = path.join(__dirname, '..', 'web');

// Active SSE Client connections (Web Admin Panels)
const sseClients = new Set();

// TiDB Connection Pool
let dbPool = null;

async function initDatabase() {
  try {
    await initTiDBSchema();
    dbPool = mysql.createPool({
      ...TIDB_CONFIG,
      waitForConnections: true,
      connectionLimit: 10,
      queueLimit: 0,
      enableKeepAlive: true,
      keepAliveInitialDelay: 10000
    });
    console.log('[TiDB] Connection Pool initialized successfully.');
  } catch (err) {
    console.error('[TiDB] Database initialization error:', err.message);
  }
}

// Broadcast an event to all connected Web Admin Dashboards
function broadcast(eventType, payload) {
  const message = `event: ${eventType}\ndata: ${JSON.stringify(payload)}\n\n`;
  for (const client of sseClients) {
    try {
      client.write(message);
    } catch (e) {
      sseClients.delete(client);
    }
  }
  console.log(`[SSE] Broadcasted '${eventType}' to ${sseClients.size} web client(s)`);
}

// Helper: send JSON response
function sendJson(res, statusCode, data) {
  res.writeHead(statusCode, {
    'Content-Type': 'application/json; charset=utf-8',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization'
  });
  res.end(JSON.stringify(data));
}

// Helper: read request body
function parseBody(req) {
  return new Promise((resolve, reject) => {
    let body = '';
    req.on('data', chunk => { body += chunk; });
    req.on('end', () => {
      if (!body) return resolve({});
      try {
        resolve(JSON.parse(body));
      } catch (err) {
        reject(err);
      }
    });
    req.on('error', reject);
  });
}

// Helper: static file MIME types
const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.apk': 'application/vnd.android.package-archive'
};

// Calculate summary stats from TiDB
async function calculateStats() {
  try {
    const [[counts], [todayCount]] = await Promise.all([
      dbPool.query(`
        SELECT 
          COUNT(*) as total,
          SUM(CASE WHEN status = 'Available' THEN 1 ELSE 0 END) as available,
          SUM(CASE WHEN status = 'Assigned' THEN 1 ELSE 0 END) as assigned,
          SUM(CASE WHEN status = 'Maintenance' THEN 1 ELSE 0 END) as maintenance,
          SUM(CASE WHEN status = 'Defective' THEN 1 ELSE 0 END) as defective
        FROM equipment_items;
      `),
      dbPool.query(`
        SELECT COUNT(*) as scanned_today 
        FROM scan_logs 
        WHERE DATE(timestamp) = CURRENT_DATE();
      `)
    ]);

    const row = counts[0] || {};
    return {
      total: Number(row.total || 0),
      available: Number(row.available || 0),
      assigned: Number(row.assigned || 0),
      maintenance: Number(row.maintenance || 0),
      defective: Number(row.defective || 0),
      scanned_today: Number(todayCount[0]?.scanned_today || 0)
    };
  } catch (err) {
    console.error('[TiDB] Error calculating stats:', err.message);
    return { total: 0, available: 0, assigned: 0, maintenance: 0, defective: 0, scanned_today: 0 };
  }
}

// HTTP Server
const server = http.createServer(async (req, res) => {
  const parsedUrl = url.parse(req.url, true);
  const pathname = parsedUrl.pathname;
  const method = req.method.toUpperCase();

  // Handle CORS preflight
  if (method === 'OPTIONS') {
    res.writeHead(204, {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization'
    });
    return res.end();
  }

  console.log(`[REQ] ${method} ${pathname}`);

  // ==========================================
  // REAL-TIME SSE ENDPOINT (Web Admin Panel)
  // ==========================================
  if (pathname === '/api/live-stream' && method === 'GET') {
    res.writeHead(200, {
      'Content-Type': 'text/event-stream; charset=utf-8',
      'Cache-Control': 'no-cache, no-transform',
      'Connection': 'keep-alive',
      'Access-Control-Allow-Origin': '*'
    });

    res.write(`event: connected\ndata: ${JSON.stringify({ status: 'connected', time: new Date() })}\n\n`);
    sseClients.add(res);

    req.on('close', () => {
      sseClients.delete(res);
      console.log(`[SSE] Client disconnected. Total active: ${sseClients.size}`);
    });
    return;
  }

  // ==========================================
  // API: AUTHENTICATION
  // ==========================================
  if (pathname === '/api/auth/login' && method === 'POST') {
    try {
      const { email, username, password } = await parseBody(req);
      const userIdentifier = (username || email || '').trim();
      const [rows] = await dbPool.query(
        'SELECT user_id, full_name, email, role FROM users WHERE (LOWER(email) = LOWER(?) OR LOWER(user_id) = LOWER(?)) AND password = ? LIMIT 1;',
        [userIdentifier, userIdentifier, password || '']
      );

      if (rows.length > 0) {
        return sendJson(res, 200, {
          success: true,
          user: rows[0]
        });
      }
      return sendJson(res, 401, { success: false, message: 'Invalid email or password' });
    } catch (e) {
      return sendJson(res, 500, { success: false, message: 'Database error: ' + e.message });
    }
  }

  // ==========================================
  // API: CHANGE PASSWORD (ACCOUNT SETTINGS)
  // ==========================================
  if (pathname === '/api/auth/change-password' && method === 'POST') {
    try {
      const { user_id, email, current_password, new_password } = await parseBody(req);
      if (!current_password || !new_password) {
        return sendJson(res, 400, { success: false, message: 'Current password and new password are required' });
      }

      if (new_password.length < 6) {
        return sendJson(res, 400, { success: false, message: 'New password must be at least 6 characters' });
      }

      let userQuery = 'SELECT * FROM users WHERE ';
      const params = [];
      if (user_id) {
        userQuery += 'user_id = ?';
        params.push(user_id);
      } else if (email) {
        userQuery += 'LOWER(email) = LOWER(?)';
        params.push(email);
      } else {
        return sendJson(res, 400, { success: false, message: 'User identification required' });
      }

      const [rows] = await dbPool.query(userQuery, params);
      if (rows.length === 0) {
        return sendJson(res, 404, { success: false, message: 'User account not found' });
      }

      const user = rows[0];
      if (user.password !== current_password) {
        return sendJson(res, 403, { success: false, message: 'Current password is incorrect' });
      }

      await dbPool.query('UPDATE users SET password = ? WHERE user_id = ?;', [new_password, user.user_id]);
      console.log(`[AUTH] Password changed successfully for user: ${user.email}`);
      return sendJson(res, 200, { success: true, message: 'Password updated successfully' });
    } catch (e) {
      return sendJson(res, 500, { success: false, message: 'Database error: ' + e.message });
    }
  }

  // ==========================================
  // API: DASHBOARD SUMMARY STATS
  // ==========================================
  if (pathname === '/api/stats' && method === 'GET') {
    const stats = await calculateStats();
    return sendJson(res, 200, { success: true, stats });
  }

  // ==========================================
  // API: EQUIPMENT CRUD (TiDB Cloud)
  // ==========================================
  if (pathname === '/api/equipment' && method === 'GET') {
    try {
      const { category, status, search } = parsedUrl.query;
      let query = 'SELECT * FROM equipment_items WHERE 1=1';
      const params = [];

      if (category && category !== 'All') {
        query += ' AND LOWER(category) = LOWER(?)';
        params.push(category);
      }
      if (status && status !== 'All') {
        query += ' AND LOWER(status) = LOWER(?)';
        params.push(status);
      }
      if (search) {
        const q = `%${search.toLowerCase()}%`;
        query += ' AND (LOWER(asset_tag) LIKE ? OR LOWER(item_name) LIKE ? OR LOWER(serial_number) LIKE ? OR LOWER(assigned_to) LIKE ? OR LOWER(location) LIKE ?)';
        params.push(q, q, q, q, q);
      }

      query += ' ORDER BY last_scanned_at DESC, created_at DESC;';
      const [items] = await dbPool.query(query, params);
      return sendJson(res, 200, { success: true, count: items.length, items });
    } catch (e) {
      return sendJson(res, 500, { success: false, message: e.message });
    }
  }

  // Lookup equipment by Asset Tag (used by mobile barcode scanner)
  if (pathname.startsWith('/api/equipment/tag/') && method === 'GET') {
    try {
      const tag = decodeURIComponent(pathname.replace('/api/equipment/tag/', ''));
      const [rows] = await dbPool.query('SELECT * FROM equipment_items WHERE LOWER(asset_tag) = LOWER(?) LIMIT 1;', [tag]);
      if (rows.length > 0) {
        return sendJson(res, 200, { success: true, exists: true, item: rows[0] });
      } else {
        return sendJson(res, 404, { success: false, exists: false, message: 'Equipment not found with tag: ' + tag });
      }
    } catch (e) {
      return sendJson(res, 500, { success: false, message: e.message });
    }
  }

  // Create new equipment item
  if (pathname === '/api/equipment' && method === 'POST') {
    try {
      const payload = await parseBody(req);
      if (!payload.asset_tag || !payload.item_name) {
        return sendJson(res, 400, { success: false, message: 'asset_tag and item_name are required' });
      }

      // Check if tag already exists in TiDB
      const [existing] = await dbPool.query('SELECT * FROM equipment_items WHERE LOWER(asset_tag) = LOWER(?) LIMIT 1;', [payload.asset_tag]);
      if (existing.length > 0) {
        if (payload.upsert) {
          const current = existing[0];
          const updated = {
            item_name: payload.item_name.trim(),
            category: payload.category || current.category || 'Laptop',
            serial_number: payload.serial_number !== undefined ? payload.serial_number : current.serial_number,
            location: payload.location || current.location || 'Computer Laboratory 1',
            status: payload.status || current.status || 'Available',
            last_scanned_at: new Date(),
            last_scanned_by: payload.last_scanned_by || 'Admin'
          };

          await dbPool.query(`
            UPDATE equipment_items SET
              item_name = ?,
              category = ?,
              serial_number = ?,
              location = ?,
              status = ?,
              last_scanned_at = ?,
              last_scanned_by = ?
            WHERE item_id = ?;
          `, [
            updated.item_name,
            updated.category,
            updated.serial_number,
            updated.location,
            updated.status,
            updated.last_scanned_at,
            updated.last_scanned_by,
            current.item_id
          ]);

          const [updatedRows] = await dbPool.query('SELECT * FROM equipment_items WHERE item_id = ?;', [current.item_id]);
          const stats = await calculateStats();
          broadcast('scan_update', { item: updatedRows[0], stats });
          return sendJson(res, 200, { success: true, message: 'Equipment updated in catalog', item: updatedRows[0], updated: true });
        } else {
          return sendJson(res, 409, { success: false, message: `Asset tag '${payload.asset_tag}' already exists` });
        }
      }

      const newItem = {
        item_id: payload.item_id || `EQ-${Date.now().toString(36).toUpperCase()}`,
        asset_tag: payload.asset_tag.trim(),
        item_name: payload.item_name.trim(),
        category: payload.category || 'Laptop',
        serial_number: payload.serial_number || 'N/A',
        status: payload.status || 'Available',
        assigned_to: payload.assigned_to || '',
        location: payload.location || 'Computer Laboratory 1',
        last_scanned_at: new Date().toISOString().slice(0, 19).replace('T', ' '),
        last_scanned_by: payload.last_scanned_by || 'Admin'
      };

      await dbPool.query(`
        INSERT INTO equipment_items 
          (item_id, asset_tag, item_name, category, serial_number, status, assigned_to, location, last_scanned_at, last_scanned_by)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
      `, [
        newItem.item_id,
        newItem.asset_tag,
        newItem.item_name,
        newItem.category,
        newItem.serial_number,
        newItem.status,
        newItem.assigned_to,
        newItem.location,
        newItem.last_scanned_at,
        newItem.last_scanned_by
      ]);

      // Auto-create initial scan log for registration
      const regLog = {
        log_id: `LOG-${Date.now()}`,
        asset_tag: newItem.asset_tag,
        item_name: newItem.item_name,
        action_type: 'New Registration',
        scanned_by: newItem.last_scanned_by,
        location: newItem.location,
        remarks: 'Registered item into IT inventory catalog',
        timestamp: new Date().toISOString()
      };

      await dbPool.query(`
        INSERT INTO scan_logs (log_id, asset_tag, item_name, action_type, scanned_by, location, remarks, timestamp)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?);
      `, [
        regLog.log_id,
        regLog.asset_tag,
        regLog.item_name,
        regLog.action_type,
        regLog.scanned_by,
        regLog.location,
        regLog.remarks,
        new Date()
      ]);

      const stats = await calculateStats();
      broadcast('scan_update', { item: newItem, log: regLog, stats });

      return sendJson(res, 201, { success: true, item: newItem, log: regLog });
    } catch (e) {
      return sendJson(res, 500, { success: false, message: e.message });
    }
  }

  // Update existing equipment
  if (pathname.startsWith('/api/equipment/') && method === 'PUT') {
    const id = pathname.replace('/api/equipment/', '');
    try {
      const payload = await parseBody(req);
      const [existing] = await dbPool.query('SELECT * FROM equipment_items WHERE item_id = ? OR asset_tag = ? LIMIT 1;', [id, id]);
      if (existing.length === 0) {
        return sendJson(res, 404, { success: false, message: 'Equipment not found' });
      }

      const current = existing[0];
      const updated = {
        item_name: payload.item_name !== undefined ? payload.item_name : current.item_name,
        category: payload.category !== undefined ? payload.category : current.category,
        serial_number: payload.serial_number !== undefined ? payload.serial_number : current.serial_number,
        status: payload.status !== undefined ? payload.status : current.status,
        assigned_to: payload.assigned_to !== undefined ? payload.assigned_to : current.assigned_to,
        location: payload.location !== undefined ? payload.location : current.location,
        last_scanned_at: new Date().toISOString().slice(0, 19).replace('T', ' '),
        last_scanned_by: payload.last_scanned_by || current.last_scanned_by
      };

      await dbPool.query(`
        UPDATE equipment_items SET
          item_name = ?,
          category = ?,
          serial_number = ?,
          status = ?,
          assigned_to = ?,
          location = ?,
          last_scanned_at = ?,
          last_scanned_by = ?
        WHERE item_id = ?;
      `, [
        updated.item_name,
        updated.category,
        updated.serial_number,
        updated.status,
        updated.assigned_to,
        updated.location,
        updated.last_scanned_at,
        updated.last_scanned_by,
        current.item_id
      ]);

      const stats = await calculateStats();
      broadcast('item_updated', { item: { ...current, ...updated }, stats });

      return sendJson(res, 200, { success: true, item: { ...current, ...updated } });
    } catch (e) {
      return sendJson(res, 500, { success: false, message: e.message });
    }
  }

  // Delete equipment
  if (pathname.startsWith('/api/equipment/') && method === 'DELETE') {
    const id = pathname.replace('/api/equipment/', '');
    try {
      const [existing] = await dbPool.query('SELECT * FROM equipment_items WHERE item_id = ? OR asset_tag = ? LIMIT 1;', [id, id]);
      if (existing.length === 0) {
        return sendJson(res, 404, { success: false, message: 'Equipment not found' });
      }
      const item = existing[0];
      await dbPool.query('DELETE FROM equipment_items WHERE item_id = ?;', [item.item_id]);

      const stats = await calculateStats();
      broadcast('item_deleted', { item_id: item.item_id, asset_tag: item.asset_tag, stats });

      return sendJson(res, 200, { success: true, message: 'Equipment deleted', item });
    } catch (e) {
      return sendJson(res, 500, { success: false, message: e.message });
    }
  }

  // ==========================================
  // API: SCAN LOGS (AUDIT TRAIL)
  // ==========================================
  if (pathname === '/api/scan-logs' && method === 'GET') {
    try {
      const limit = parseInt(parsedUrl.query.limit) || 100;
      const [logs] = await dbPool.query('SELECT * FROM scan_logs ORDER BY timestamp DESC LIMIT ?;', [limit]);
      return sendJson(res, 200, { success: true, count: logs.length, logs });
    } catch (e) {
      return sendJson(res, 500, { success: false, message: e.message });
    }
  }

  // ==========================================
  // API: ATOMIC SCAN ACTION (Mobile App Core Endpoint)
  // ==========================================
  if (pathname === '/api/scan' && method === 'POST') {
    try {
      const payload = await parseBody(req);
      const { asset_tag, action_type, scanned_by, location, remarks, new_status, assigned_to } = payload;

      if (!asset_tag) {
        return sendJson(res, 400, { success: false, message: 'asset_tag is required' });
      }

      const [existing] = await dbPool.query('SELECT * FROM equipment_items WHERE LOWER(asset_tag) = LOWER(?) LIMIT 1;', [asset_tag]);
      let targetItem = null;
      const now = new Date();
      const nowIso = now.toISOString();

      if (existing.length > 0) {
        targetItem = existing[0];
        const statusToSet = new_status || (action_type === 'Report Defective' ? 'Defective' : targetItem.status);
        const locToSet = location || targetItem.location;
        const assignToSet = assigned_to !== undefined ? assigned_to : targetItem.assigned_to;

        await dbPool.query(`
          UPDATE equipment_items SET
            status = ?,
            location = ?,
            assigned_to = ?,
            last_scanned_at = ?,
            last_scanned_by = ?
          WHERE item_id = ?;
        `, [
          statusToSet,
          locToSet,
          assignToSet,
          now,
          scanned_by || 'Mobile Scanner',
          targetItem.item_id
        ]);

        targetItem.status = statusToSet;
        targetItem.location = locToSet;
        targetItem.assigned_to = assignToSet;
        targetItem.last_scanned_at = nowIso;
        targetItem.last_scanned_by = scanned_by || 'Mobile Scanner';
      } else {
        // Auto-register item into equipment_items catalog so no prior registration is needed
        const autoItemId = `EQ-${Date.now().toString(36).toUpperCase()}`;
        const autoName = payload.item_name || `Equipment (${asset_tag})`;
        const autoCategory = payload.category || 'Hardware';
        const autoLoc = location || 'Computer Laboratory 1';

        await dbPool.query(`
          INSERT INTO equipment_items 
            (item_id, asset_tag, item_name, category, serial_number, status, location, last_scanned_at, last_scanned_by)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
        `, [
          autoItemId,
          asset_tag,
          autoName,
          autoCategory,
          payload.serial_number || 'N/A',
          new_status || 'Available',
          autoLoc,
          now,
          scanned_by || 'Mobile Scanner'
        ]);

        targetItem = {
          item_id: autoItemId,
          asset_tag: asset_tag,
          item_name: autoName,
          category: autoCategory,
          serial_number: payload.serial_number || 'N/A',
          status: new_status || 'Available',
          location: autoLoc,
          last_scanned_at: nowIso,
          last_scanned_by: scanned_by || 'Mobile Scanner'
        };
      }

      // Record Scan Log in TiDB
      const newLog = {
        log_id: `LOG-${Date.now()}`,
        asset_tag: asset_tag,
        item_name: targetItem ? targetItem.item_name : (payload.item_name || 'Unregistered Equipment'),
        action_type: action_type || 'Audit Verify',
        scanned_by: scanned_by || 'Mobile Scanner',
        location: location || (targetItem ? targetItem.location : 'Unknown'),
        remarks: remarks || 'Verified via mobile barcode scanner',
        timestamp: nowIso
      };

      await dbPool.query(`
        INSERT INTO scan_logs (log_id, asset_tag, item_name, action_type, scanned_by, location, remarks, timestamp)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?);
      `, [
        newLog.log_id,
        newLog.asset_tag,
        newLog.item_name,
        newLog.action_type,
        newLog.scanned_by,
        newLog.location,
        newLog.remarks,
        now
      ]);

      // Push real-time SSE update to Web Admin Panels
      const stats = await calculateStats();
      broadcast('scan_update', { item: targetItem, log: newLog, stats });

      return sendJson(res, 200, {
        success: true,
        message: 'Scan recorded and synchronized successfully to TiDB Cloud',
        item: targetItem,
        log: newLog
      });
    } catch (e) {
      return sendJson(res, 500, { success: false, message: e.message });
    }
  }

  // ==========================================
  // STATIC FILE HANDLER (Web Admin Dashboard)
  // ==========================================
  let filePath = path.join(WEB_DIR, pathname === '/' ? 'index.html' : pathname);
  
  if (!fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) {
    filePath = path.join(WEB_DIR, 'index.html');
  }

  const ext = path.extname(filePath).toLowerCase();
  const contentType = MIME_TYPES[ext] || 'application/octet-stream';

  fs.readFile(filePath, (err, content) => {
    if (err) {
      res.writeHead(404, { 'Content-Type': 'text/plain' });
      return res.end('404 Not Found');
    }
    const isStaticAsset = ext === '.css' || ext === '.js' || ext === '.png' || ext === '.jpg' || ext === '.svg' || ext === '.ico';
    const headers = {
      'Content-Type': contentType,
      'Cache-Control': isStaticAsset ? 'public, max-age=86400, stale-while-revalidate=604800' : 'no-cache, must-revalidate',
      'X-Content-Type-Options': 'nosniff'
    };
    res.writeHead(200, headers);
    res.end(content);
  });
});

// Start Server
initDatabase().then(() => {
  server.listen(PORT, '0.0.0.0', () => {
    console.log(`\n======================================================`);
    console.log(`🚀 IT Equipment Inventory Server RUNNING`);
    console.log(`🌐 Web Admin Panel: http://localhost:${PORT}`);
    console.log(`☁️  TiDB Cloud DB: ${TIDB_CONFIG.host}:${TIDB_CONFIG.port}/${TIDB_CONFIG.database}`);
    console.log(`📡 Real-Time SSE Stream: http://localhost:${PORT}/api/live-stream`);
    console.log(`📱 Android Emulator URL: http://10.0.2.2:${PORT}`);
    console.log(`======================================================\n`);
  });
}).catch(err => {
  console.error('Fatal startup error:', err);
});

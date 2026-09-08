import http from 'node:http';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
const root = fileURLToPath(new URL('.', import.meta.url));
http.createServer(async (req, res) => {
  try {
    const pathname = decodeURIComponent(new URL(req.url, 'http://localhost').pathname);
    const file = path.resolve(root, '.' + (pathname === '/' ? '/index.html' : pathname));
    if (!file.startsWith(root)) { res.writeHead(403).end(); return; }
    const data = await readFile(file);
    res.setHeader('Content-Type', ({'.html':'text/html','.css':'text/css','.js':'text/javascript','.png':'image/png'})[path.extname(file)] || 'application/octet-stream');
    res.end(data);
  } catch { res.writeHead(404).end('Not found'); }
}).listen(4173, '0.0.0.0', () => console.log('Rescue Room: http://0.0.0.0:4173'));

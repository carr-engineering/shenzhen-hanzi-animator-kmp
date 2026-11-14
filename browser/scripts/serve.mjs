import http from 'node:http';
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const types = {'.html':'text/html; charset=utf-8', '.js':'text/javascript', '.mjs':'text/javascript', '.css':'text/css', '.json':'application/json', '.hzb':'application/octet-stream'};
const port = Number(process.env.PORT ?? 8085);
http.createServer(async (request, response) => {
  const pathname = decodeURIComponent(new URL(request.url ?? '/', 'http://localhost').pathname);
  const relative = pathname === '/' ? 'demo/index.html' : pathname.slice(1);
  const file = path.resolve(root, relative);
  if (!file.startsWith(`${root}${path.sep}`)) { response.writeHead(403); response.end(); return; }
  try {
    let content, encoding;
    const accept = request.headers['accept-encoding'] ?? '';
    if (file.endsWith('.hzb')) {
      for (const [token, extension] of [['br', '.br'], ['gzip', '.gz']]) if (accept.includes(token)) { try { content = await readFile(file + extension); encoding = token; break; } catch {} }
    }
    content ??= await readFile(file);
    response.writeHead(200, {'Content-Type': types[path.extname(file)] ?? 'application/octet-stream', 'Cache-Control':'no-cache', ...(encoding ? {'Content-Encoding':encoding, 'Vary':'Accept-Encoding'} : {})}); response.end(content);
  } catch { response.writeHead(404); response.end('Not found'); }
}).listen(port, '127.0.0.1', () => console.log(`Demo: http://127.0.0.1:${port}`));

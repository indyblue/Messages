#!/usr/bin/env -S deno run --allow-net
/* global Deno */
import { serveDir } from 'jsr:@std/http/file-server';

const API_PROXY_TARGET = 'http://192.168.200.107:27874';

async function proxyApiRequest(req, apiProxyTarget) {
  const url = new URL(req.url);
  const apiUrl = new URL(url.pathname + url.search, apiProxyTarget);
  // Clone headers and set proxy headers
  const headers = new Headers(req.headers);
  headers.set('Host', apiUrl.host);
  // Compose X-Forwarded-For header for proxy chains
  const priorForwardedFor = req.headers.get('x-forwarded-for');
  const realIp = req.headers.get('x-real-ip') || '';
  const forwardedFor = priorForwardedFor ? `${priorForwardedFor}, ${realIp}` : realIp;
  headers.set('X-Forwarded-For', forwardedFor);
  // Compose X-Forwarded-Proto header for proxy chains
  const priorForwardedProto = req.headers.get('x-forwarded-proto');
  const currentProto = url.protocol.replace(':', '');
  const forwardedProto = priorForwardedProto
    ? `${priorForwardedProto}, ${currentProto}` : currentProto;
  headers.set('X-Forwarded-Proto', forwardedProto);
  if (req.headers.get('upgrade')) {
    headers.set('Upgrade', req.headers.get('upgrade'));
    headers.set('Connection', req.headers.get('connection') || 'upgrade');
  }
  const apiReq = new Request(apiUrl, {
    method: req.method,
    headers,
    body: req.body,
  });
  const apiRes = await fetch(apiReq);
  return new Response(apiRes.body, {
    status: apiRes.status,
    headers: apiRes.headers,
  });
}

async function handler(req) {
  const url = new URL(req.url);

  // Reverse proxy for /api
  if (url.pathname.startsWith('/api')) {
    return proxyApiRequest(req, API_PROXY_TARGET);
  }

  // Serve static files from ./public using serveDir
  return serveDir(req, { fsRoot: './public' });
}

console.log('Server running on http://localhost:8000');
Deno.serve({ port: 8000 }, handler);

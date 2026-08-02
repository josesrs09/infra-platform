const express = require('express');
const client = require('prom-client');

const app = express();
const port = Number(process.env.PORT || 3000);
const application = process.env.APPLICATION_NAME || 'node-api';
const environment = process.env.ENVIRONMENT || 'development';

client.collectDefaultMetrics({ prefix: 'app_' });

const requests = new client.Counter({
  name: 'http_requests_total',
  help: 'Total HTTP requests',
  labelNames: ['application', 'environment', 'method', 'route', 'status_code']
});

const duration = new client.Histogram({
  name: 'http_request_duration_seconds',
  help: 'HTTP request duration in seconds',
  labelNames: ['application', 'environment', 'method', 'route', 'status_code'],
  buckets: [0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2, 5]
});

app.use((req, res, next) => {
  const stop = duration.startTimer();
  res.on('finish', () => {
    const route = req.route?.path || req.baseUrl || req.path || 'unknown';
    const labels = {
      application,
      environment,
      method: req.method,
      route,
      status_code: String(res.statusCode)
    };
    requests.inc(labels);
    stop(labels);
  });
  next();
});

app.get('/health', (_req, res) => res.json({ status: 'UP', application }));
app.get('/api/example', (_req, res) => res.json({ ok: true }));
app.get('/api/error', (_req, res) => res.status(500).json({ error: 'Simulated error' }));
app.get('/metrics', async (_req, res) => {
  res.set('Content-Type', client.register.contentType);
  res.end(await client.register.metrics());
});

app.listen(port, () => console.log(`${application} listening on ${port}`));

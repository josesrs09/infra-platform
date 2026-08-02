import amqp from 'amqplib';

const rabbitUrl = process.env.RABBITMQ_URL;
const botToken = process.env.TELEGRAM_BOT_TOKEN;
const chatId = process.env.TELEGRAM_CHAT_ID;
const queues = (process.env.ERROR_QUEUES || 'app.errors.critical,app.errors.warning,app.errors.info')
  .split(',')
  .map((value) => value.trim())
  .filter(Boolean);

if (!rabbitUrl || !botToken || !chatId) {
  throw new Error('RABBITMQ_URL, TELEGRAM_BOT_TOKEN y TELEGRAM_CHAT_ID son obligatorios.');
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function escapeHtml(value = '') {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;');
}

function formatMessage(queue, payload) {
  const severity = payload.severity || queue.split('.').at(-1) || 'error';
  const application = payload.application || payload.app || 'aplicación-desconocida';
  const environment = payload.environment || 'unknown';
  const timestamp = payload.timestamp || new Date().toISOString();
  const message = payload.message || payload.error || JSON.stringify(payload);
  const correlationId = payload.correlationId || payload.traceId || payload.requestId || 'N/D';

  return [
    `<b>ALERTA DE APLICACIÓN — ${escapeHtml(severity.toUpperCase())}</b>`,
    `<b>Aplicación:</b> ${escapeHtml(application)}`,
    `<b>Ambiente:</b> ${escapeHtml(environment)}`,
    `<b>Fecha:</b> ${escapeHtml(timestamp)}`,
    `<b>Correlation ID:</b> ${escapeHtml(correlationId)}`,
    `<b>Mensaje:</b> ${escapeHtml(message).slice(0, 2500)}`,
  ].join('\n');
}

async function sendTelegram(text) {
  const response = await fetch(`https://api.telegram.org/bot${botToken}/sendMessage`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({
      chat_id: chatId,
      text,
      parse_mode: 'HTML',
      disable_web_page_preview: true,
    }),
  });

  if (!response.ok) {
    throw new Error(`Telegram respondió ${response.status}: ${await response.text()}`);
  }
}

async function start() {
  while (true) {
    try {
      const connection = await amqp.connect(rabbitUrl);
      const channel = await connection.createChannel();
      await channel.prefetch(10);

      connection.on('close', () => process.exit(1));
      connection.on('error', (error) => console.error('RabbitMQ connection error:', error));

      for (const queue of queues) {
        await channel.assertQueue(queue, { durable: true });
        await channel.consume(queue, async (message) => {
          if (!message) return;

          try {
            const raw = message.content.toString('utf8');
            let payload;
            try {
              payload = JSON.parse(raw);
            } catch {
              payload = { message: raw };
            }

            await sendTelegram(formatMessage(queue, payload));
            channel.ack(message);
          } catch (error) {
            console.error(`No se pudo procesar mensaje de ${queue}:`, error);
            channel.nack(message, false, false);
          }
        }, { noAck: false });
      }

      console.log(`Consumidor iniciado para: ${queues.join(', ')}`);
      await new Promise(() => {});
    } catch (error) {
      console.error('No se pudo conectar a RabbitMQ:', error);
      await sleep(10000);
    }
  }
}

start().catch((error) => {
  console.error(error);
  process.exit(1);
});

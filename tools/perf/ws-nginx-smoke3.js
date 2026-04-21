import ws from 'k6/ws';
export default function () {
  const res = ws.connect('ws://localhost/ws/market-native', { headers: { 'X-K6-Bypass-Key': 'k6-bypass-20260420' } }, function (socket) {
    socket.on('open', function () {
      socket.send('CONNECT\naccept-version:1.2\nhost:localhost\n\n\u0000');
      socket.setTimeout(function () { socket.close(); }, 3000);
    });
    socket.on('message', function (msg) { console.log(String(msg).slice(0, 80)); });
  });
  console.log('status=' + res.status + ' error=' + (res.error || ''));
}

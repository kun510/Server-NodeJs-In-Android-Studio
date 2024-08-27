var app = require('express')();
var http = require('http').Server(app);
var io = require('socket.io')(http);

app.get('/', function(req, res) {
    res.sendFile(__dirname + '/index.html');
});

io.on('connection', function(socket) {
  console.log('a user connected');

  socket.on('stream', function(data) {
    io.emit('stream', data);
     console.log('send Data stream',data);
  });

  socket.on('disconnect', function() {
    console.log('user disconnected');
  });
});

http.listen(5555, function() {
  console.log('listening on *:5555');
});

# Hotel_Sockets

## Running the project

1. Compile all java files into out/production/Hotel_Sockets/<respective_package> with `javac -d <destination_dir>/ <origin_dir>/*.java`
2. Easy one liner compiler is `javac -d out/production/Hotel_Sockets server/*.java && javac -d out/production/Hotel_Sockets shared/*.java && javac -d out/production/Hotel_Sockets client/*.java`
3. Start up the server with `java -cp out/production/Hotel_Sockets server.StartChatServer`
4. Connect however many clients you want by running in a seperate terminal `java -cp out/production/Hotel_Sockets client.startClient`

## User guide

### Basic Commands
1. /register <username> - Register with a username
2. /name - Show your current username
3. /exit - Exit the chat

### Group Commands
1. /group <flag> <groupName>
  1. create - Create a new group
  2. join - Join an existing group
  3. leave - Leave a group
  4. remove - Remove a group
  5. list - Show all available groups

### Topic Commands
1. /topic <flag> <topicName>
  1. create - Create a new topic
  2. subscribe - Subscribe to a topic
  3. unsubscribe - Unsubscribe from a topic
  4. list - List all available topics

### User Commands
1. /user <flag>
  1. list - Show a list of all online users
  2. count - Show the number of users online

### Message Commands
1. /send <target> <message> - Send a message to a user or group (old format)
2. /send user <username> <message> - Send a direct message to a specific user
3. /send group <groupname> <message> - Send a message to a specific group
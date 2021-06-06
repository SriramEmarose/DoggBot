#--------------------------------------------------------------------
# Implements WiFi controlled Robot Controller
#
# Meant for: https://github.com/SriramEmarose/DoggyBot
#
# Author: Sriram Emarose [sriram.emarose@gmail.com]
#
#
#
#--------------------------------------------------------------------

#include <ESP8266WiFi.h>
#include <stdlib.h>
 
const char* ssid = "wifiName";
const char* password = "pwd";
 
// Right Motor
const uint8_t motorR_en = D1;
const uint8_t motorR_1 = D2;
const uint8_t motorR_2 = D3;

//Left Motor
const uint8_t motorL_en = D7;
const uint8_t motorL_1  = D6;
const uint8_t motorL_2  = D5;

static const int MOVE_ROBOT_FORWARD       = 1;
static const int MOVE_ROBOT_REVERSE       = 2;
static const int MOVE_ROBOT_RIGHT         = 3;
static const int MOVE_ROBOT_LEFT          = 4;
static const int MOVE_ROBOT_SLIGHT_RIGHT  = 5;
static const int MOVE_ROBOT_SLIGHT_LEFT   = 6;
static const int STOP_ROBOT               = 7;
static const int CMD_ACK                  = 8;
static const int INCORRECT_CMD_ACK        = 9;

WiFiServer server(80);


const int gANGLE_OFFSET_THRESHOLD = 5;

int PWM_HALF_DUTY_CYCLE = 100;
int PWM_MORE_HALF_DUTY_CYCLE = 1024;
int PWM_FULL_DUTY_CYCLE = 1024;


void Init_Off_Motors()
{
  pinMode(motorR_en, OUTPUT);
  pinMode(motorL_en, OUTPUT);
  
  pinMode(motorR_1, OUTPUT);
  digitalWrite(motorR_1, 0);
  
  pinMode(motorR_2, OUTPUT);
  digitalWrite(motorR_2, 0);

  pinMode(motorL_1, OUTPUT);
  digitalWrite(motorL_1, 0);
  
  pinMode(motorL_2, OUTPUT);
  digitalWrite(motorL_2, 0);
}

void Forward()
{
  analogWrite(motorR_en, PWM_MORE_HALF_DUTY_CYCLE);
  analogWrite(motorL_en, PWM_MORE_HALF_DUTY_CYCLE);
  
  digitalWrite(motorR_1, LOW);  
  digitalWrite(motorR_2, HIGH);

  digitalWrite(motorL_1, LOW);  
  digitalWrite(motorL_2, HIGH);
}

void Reverse()
{
  analogWrite(motorR_en, PWM_MORE_HALF_DUTY_CYCLE);
  analogWrite(motorL_en, PWM_MORE_HALF_DUTY_CYCLE);
  
  digitalWrite(motorR_1, HIGH);  
  digitalWrite(motorR_2, LOW);

  digitalWrite(motorL_1, HIGH);  
  digitalWrite(motorL_2, LOW);
}

void Right()
{
  analogWrite(motorR_en, PWM_MORE_HALF_DUTY_CYCLE);
  analogWrite(motorL_en, PWM_MORE_HALF_DUTY_CYCLE);
  
  digitalWrite(motorR_1, HIGH);  
  digitalWrite(motorR_2, LOW);

  digitalWrite(motorL_1, LOW);  
  digitalWrite(motorL_2, HIGH);
    
}


void Left()
{
  analogWrite(motorR_en, PWM_MORE_HALF_DUTY_CYCLE);
  analogWrite(motorL_en, PWM_MORE_HALF_DUTY_CYCLE);
  
  digitalWrite(motorR_1, LOW);  
  digitalWrite(motorR_2, HIGH);

  digitalWrite(motorL_1, HIGH);  
  digitalWrite(motorL_2, LOW);  
}



void setup() {
 
  Serial.begin(9600);
  delay(10);

 Init_Off_Motors();

  // Connect to WiFi network
  Serial.println();
  Serial.println();
  Serial.print("Connecting to ");
  Serial.println(ssid);
 
  WiFi.begin(ssid, password);
 
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("");
  Serial.println("WiFi connected");
 
  // Start the server
  server.begin();
  Serial.println("Server started");
 
  // Print the IP address
  Serial.print("Use this URL to connect: ");
  Serial.print("http://");
  Serial.print(WiFi.localIP());
  Serial.println("/"); 
}

int gTargetAngle = 0;
int gIsRobotFwd = 0;
int gIsRobotRev = 0;
void loop() {
 
 WiFiClient client = server.available();
 
  if (client) {
 
    while (client.connected()) {
 
      while (client.available()>0) 
      {
        char c = client.read();
        //Serial.write(c);
        int cmd = int(c);
        Serial.write(cmd);

        switch(cmd)
        {
          case '1':
          case '11':
          {
            Serial.write("MOVE_ROBOT_FORWARD");
            Forward();
            client.println(MOVE_ROBOT_FORWARD);
            break;
          }
          case '2':
          case '22':
          {
            Serial.write("MOVE_ROBOT_REVERSE");
            Reverse();
            client.println(MOVE_ROBOT_REVERSE);
            break;
          }
          case '3':
          case 3:
          {
            Serial.write("MOVE_ROBOT_RIGHT");
            Right();
            client.println(MOVE_ROBOT_RIGHT);
            break;
          }
          case '4':
          case 4:
          {
            Serial.write("MOVE_ROBOT_LEFT");
            Left();
            client.println(MOVE_ROBOT_LEFT);
            break;
          }          
          case '7':
          case 7:
          {
            Serial.write("STOP_ROBOT");
            Init_Off_Motors();
            client.println(STOP_ROBOT);
            break;
          }
          default:
          {
            Serial.write("CMD_ACK");
            //Init_Off_Motors();
            client.println(CMD_ACK);
            break;
          }
        }        
      } 
      delay(0.1);
    }
 
    client.stop();
    Serial.println("Client disconnected");
 
  }
 
}

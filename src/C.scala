import akka.actor._
import com.typesafe.config.ConfigFactory
import java.net.InetAddress
import scala.collection.mutable.ArrayBuffer
import akka.routing.RoundRobinRouter
import java.security.MessageDigest
import scala.util.control.Breaks
import scala.util.Random
import java.security.MessageDigest

sealed trait Bits
case class work(message: String) extends Bits
case class r(hex: ArrayBuffer[String]) extends Bits
case class count(start: Int, end: Int, message: String,num_of_zeros:Int) extends Bits
case class print(ans: ArrayBuffer[String]) extends Bits


case class LocalMessage(ab:ArrayBuffer[String]) extends Bits
case class Client_Result(input:ArrayBuffer[String])  extends Bits
case class Initiate(num_of_zeros:Int,message: String) extends Bits
case class Start_client_master(num_of_zeros:Int,message: String) extends Bits

case class Reply_To_Client_Master(output: ArrayBuffer[String] ) extends Bits

/* Main object*/
object C {

  def main(args: Array[String]) {
    val hostname = InetAddress.getLocalHost.getHostName
    val config = ConfigFactory.parseString(
      """akka{
		  		actor{
		  			provider = "akka.remote.RemoteActorRefProvider"
		  		}
		  		remote{
                   enabled-transports = ["akka.remote.netty.tcp"]
		  			netty.tcp{
						hostname = "127.0.0.1"
						port = 0
					}
				}     
    	}""")

    implicit val system = ActorSystem("LocalSystem", ConfigFactory.load(config))
	
	val Send_To_Server = system.actorOf(Props(new Send_To_Server(args(0))), name = "Send_To_Server") // the local actor
	val listener = system.actorOf(Props(new Listener(Send_To_Server)), name = "listener")
    val client_master =  system.actorOf(Props(new client_master(listener)), name = "master")
	val Server_ask = system.actorOf(Props(new Server_ask(args(0),client_master)), name = "Server_ask") // the local actor
    
    Server_ask ! "WANT_WORK" 
    
  }
}

/* It sends request to master requesting for work*/

class Server_ask(ip: String , client_master:ActorRef) extends Actor { 
  
  val remote = context.actorFor("akka.tcp://HelloRemoteSystem@" + ip + ":3553/user/Remote_Actor") 
  def receive = {
    case "WANT_WORK" =>
      println("In WANT_WORK") 
      remote ! "Request_Work"
    case Initiate(num_of_zeros,message) =>
       client_master ! Start_client_master(num_of_zeros:Int, message: String)      
       case _ =>
      println("Message is Unknown")
  }
}

/* It sends the generated bit coins to master */
class Send_To_Server(ip: String) extends Actor {
 
  println("akka.tcp://HelloRemoteSystem@" + ip + ":3553/user/RemoteActor")
  
  val remote = context.actorFor("akka.tcp://HelloRemoteSystem@" + ip + ":3553/user/Remote_Actor")

  def receive = {
    case LocalMessage(input) =>      
       println("Going to Remote")
       remote ! Client_Result(input)
       println("Sent") 
    case _ =>
      println("Message Unknown")
  }
}

/* It creates workers(2) and assigns work using round robin method*/ 
class client_master( listener: ActorRef) extends Actor {

    var num = 0: Int
    var result: ArrayBuffer[String] = new ArrayBuffer[String]();
	val start: Long = System.currentTimeMillis
	var message_count=100: Int 
	var workers_count=2 :Int
    def receive = {
	  case Start_client_master(num_of_zeros:Int, message: String ) =>
	  val worker = context.actorOf(Props[Worker].withRouter(RoundRobinRouter(workers_count)), name = "worker")
	  var a =10 :Int
      for (i <- 1 to message_count) {
        worker ! count(a,a+1, message,num_of_zeros)
         a +=2 
      if (a==20) a=10
	}
        
      case r(ans) =>
        { result ++= (ans); num += 1 }
        if (num == message_count) {
		listener ! print(result)
		context.stop(self)
		}
	
    }
  }

/* It generates the based coin*/
class Worker extends Actor {

	def receive = {
      case count(start: Int, end: Int, message: String,num_of_zeros:Int) => generateBitCoins(start, end, message,num_of_zeros)
    }
    
def generateBitCoins(start: Int, end: Int, message: String,num_of_zeros:Int) = {
      var result: ArrayBuffer[String] = new ArrayBuffer[String]();
      for (j <- 1 to 100000) {
        for (i <- start to end) {
          val chars = ('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')
          val sb = new StringBuilder
          for(j<-1 to i )
          {
            val randomNum = util.Random.nextInt(chars.length)
            sb.append(chars(randomNum))
          }
          var s: String = sb.toString
          hashMap( message +s, result,num_of_zeros)
        }
      }
      sender ! r(result)
    }
    def hashMap(s: String, result: ArrayBuffer[String],num_of_zeros:Int) = {
      val mdbytes: Array[Byte] = MessageDigest.getInstance("sha-256").digest(s.getBytes())
      val hexString: StringBuffer = new StringBuffer();
      for (i <- 0 to mdbytes.length - 1) {
        var str: String = Integer.toHexString(0xFF & mdbytes(i));
        if (str.length() == 1) str = "0" + str;
        hexString.append(str);
      }
      var search_key:String=""
      for(i<-0 until num_of_zeros)
        search_key+="0";
      if (hexString.toString.startsWith(search_key))
      {
        result+=s
        result += (hexString.toString)
      }
    }
	
  }

class Listener(Send_To_Server: ActorRef) extends Actor {
  def receive = {
    case print(result) =>
      {
        println("Sending to Server")
        Send_To_Server ! LocalMessage(result)
        println("Sent to Server")
      }  
  }
}
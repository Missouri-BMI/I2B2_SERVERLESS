package net.shrine.email

import com.typesafe.config.ConfigFactory
import courier.Defaults.executionContext
import courier.{Envelope, Mailer, Text}

import javax.mail.Provider
import javax.mail.internet.InternetAddress
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.jvnet.mock_javamail.{Mailbox, MockTransport}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.language.implicitConversions

/**
  * Source of typesafe config for the problems database
  *
  * @author david
  * @since 1.22
  */
class EmailTest {

  implicit def stringToInternetAddress(string:String):InternetAddress = new InternetAddress(string)

  @Test
  def testSendMockedEmail(): Unit = {

    class MockedSMTPProvider extends Provider(Provider.Type.TRANSPORT, "mocked", classOf[MockTransport].getName, "Mock", null)

    val config = ConfigFactory.load("shrine.conf")

    val mockedSession = ConfiguredMailer.createSessionFromConfig(config.getConfig("shrine.email"))
    mockedSession.setProvider(new MockedSMTPProvider)

    val mailer:Mailer = Mailer(mockedSession)

    val envelope:Envelope = Envelope(from = "someone@example.com").to("mom@gmail.com").cc("dad@gmail.com").subject("miss you").content(Text("hi mom"))

    val future: Future[Unit] = mailer(envelope)

    Await.ready(future, 60.seconds)

    val momsInbox: Mailbox = Mailbox.get("mom@gmail.com")
    assert(momsInbox.size == 1)

    val momsMsg = momsInbox.get(0)
    assertEquals("hi mom",momsMsg.getContent)
    assertEquals("miss you",momsMsg.getSubject)
  }
}

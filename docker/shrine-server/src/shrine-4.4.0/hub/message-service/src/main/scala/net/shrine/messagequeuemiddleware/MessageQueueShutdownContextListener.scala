package net.shrine.messagequeuemiddleware

import javax.servlet.{ServletContextEvent, ServletContextListener}
import net.shrine.log.Log

/**
  * Created by yifan on 8/31/17.
  */

class MessageQueueShutdownContextListener extends ServletContextListener {


  override def contextInitialized(servletContextEvent: ServletContextEvent): Unit = {
    Log.debug(s"${getClass.getSimpleName} context initialized $servletContextEvent")

  }

  override def contextDestroyed(servletContextEvent: ServletContextEvent): Unit = {
    LocalMessageQueueStopper.stop()
    Log.debug(s"${getClass.getSimpleName} context destroyed $servletContextEvent")
  }
}
package net.shrine.authentication

/**
 * @author clint
 * @since Jul 1, 2014
 */
sealed trait AuthenticationType {
  def name:String
}

object AuthenticationType {
  case object Pm extends AuthenticationType{
    val name = "pm"
  }

  case object Sso extends AuthenticationType{
    val name = "sso"
  }

  val authenticationTypes = Seq(Pm,Sso)

  val namesToAuthenticationTypes: Map[String, AuthenticationType] = authenticationTypes.map(a => a.name -> a).toMap
}
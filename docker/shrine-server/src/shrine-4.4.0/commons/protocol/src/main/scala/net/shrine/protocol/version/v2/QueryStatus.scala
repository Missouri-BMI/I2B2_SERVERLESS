package net.shrine.protocol.version.v2

import io.circe.Codec
import io.circe.generic.extras.semiauto.deriveEnumerationCodec

/**
  * States of queries.
  *
  * @author david
  * @since 1.26
  */
sealed abstract class QueryStatus(
                             val statusName:String,
                             val stage:Int,
                             val uiString:String,
                             val isFinal:Boolean,
                             val isError:Boolean
                           ) extends Product with Serializable

object QueryStatus {

  implicit val codec: Codec[QueryStatus] = deriveEnumerationCodec[QueryStatus]

  case object IdAssigned extends QueryStatus("Id Assigned",100,"Submitted",false, false)
  case object SentToHub extends QueryStatus("Sent to Hub",200,"Submitted",false, false) //state not yet recorded at the hub
  case object ReceivedAtHub extends QueryStatus("Received by Hub",300,"Submitted",false, false)
  case object UnknownInTransit extends QueryStatus("Unknown in Transit", 400,"Submitted", false, false)
  case object ReadyForAdapters extends QueryStatus("Ready for Adapters",500,"In Progress",false, false)
  case object SentToAdapters extends QueryStatus("Sent to Adapters",600,"In Progress",true, false)
  case object QepError extends QueryStatus("QepError",1000,"Submission Error",true, true)
  case object HubError extends QueryStatus("HubError",1100, "Network Error",true, true)
  case object UnknownFinal extends QueryStatus("Unknown Final",1200,"Network Error", true, true)

  val statuses: Seq[QueryStatus] = Seq(
    IdAssigned,
    SentToHub,
    ReceivedAtHub,
    UnknownInTransit,
    ReadyForAdapters,
    SentToAdapters,
    UnknownFinal,
    QepError,
    HubError,
  )

  lazy val namesToStatuses: Map[String, QueryStatus] = statuses.map(status => status.statusName -> status).toMap

}
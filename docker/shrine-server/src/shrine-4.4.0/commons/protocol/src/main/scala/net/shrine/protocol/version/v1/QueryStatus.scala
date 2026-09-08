package net.shrine.protocol.version.v1

/**
 * States of queries.
 *
 * @author david
 * @since 1.26
 */
sealed abstract class QueryStatus(
                                   val statusName:String,
                                   val uiString:String,
                                   val isFinal:Boolean,
                                   val isError:Boolean
                                 ) extends Product with Serializable

object QueryStatuses {

  case object IdAssigned extends QueryStatus("Id Assigned","Submitted",false, false)
  case object SentToHub extends QueryStatus("Sent to Hub","Submitted",false, false) //state not yet recorded at the hub
  case object ReceivedAtHub extends QueryStatus("Received by Hub","Submitted",false, false)
  case object UnknownInTransit extends QueryStatus("Unknown in Transit","Submitted", false, false)
  case object ReadyForAdapters extends QueryStatus("Ready for Adapters","In Progress",false, false)
  case object SentToAdapters extends QueryStatus("Sent to Adapters","In Progress",true, false)
  case object QepError extends QueryStatus("QepError","Submission Error",true, true)
  case object HubError extends QueryStatus("HubError","Network Error",true, true)
  case object UnknownFinal extends QueryStatus("Unknown Final","Network Error", true, true)
  case object BeforeV26 extends QueryStatus("Before V1 Protocol","Completed", true, false)  //todo Change to Shrine 2

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
    BeforeV26
  )

  lazy val namesToStatuses: Map[String, QueryStatus] = statuses.map(status => status.statusName -> status).toMap

}
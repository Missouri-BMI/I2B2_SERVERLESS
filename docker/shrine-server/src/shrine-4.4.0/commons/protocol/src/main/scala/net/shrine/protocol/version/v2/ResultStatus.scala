package net.shrine.protocol.version.v2

import io.circe.Codec
import io.circe.generic.extras.semiauto.deriveEnumerationCodec

/**
  * States of potential and final results.
  *
  * @author david
  * @since 1.26
  */
sealed abstract class ResultStatus(
                             val statusName:String,
                             val stage:Int,
                             val uiString:String,
                             val isInCrC:Boolean,
                             val isFinal:Boolean,
                             val isError:Boolean
                           ) extends Product with Serializable {
  def before(that: ResultStatus):Boolean = {
    that.stage < this.stage
  }
}

object ResultStatus {

  implicit val codec: Codec[ResultStatus] = deriveEnumerationCodec[ResultStatus]

  case object IdAssigned extends ResultStatus("Id Assigned",100,"Processing at Hub",false,false, false)
  case object SentToAdapter extends ResultStatus("Sent To Adapter",200,"Sent to Site",false,false, false)
  case object ReceivedByAdapter extends ResultStatus("Received By Adapter",300,"Processing at Site",false,false, false)
  case object QueuedByAdapter extends ResultStatus("Queued By Adapter",400,"Delayed At Site",false, false, false)
  case object QueuedForManualSubmission extends ResultStatus("Queued By Adapter",500,"Delayed At Site",false, false, false)
  case object ReadyToSubmit extends ResultStatus("Ready To Submit",600,"Processing at Site", false, false, false) //state not yet recorded at the hub, not used yet
  case object SubmittedToCRC extends ResultStatus("Submitted To CRC",700,"Processing at Site",true, false, false)
  case object QueuedByCRC extends ResultStatus("Queued By CRC",800,"Delayed At Site",true,false, false)
  case object UnknownWhileQueuedByCRC extends ResultStatus("Unknown While Queued By CRC",900,"Delayed At Site",true,false, false)
  case object UnknownInTransit extends ResultStatus("Unknown Not Final",1000,"Delayed At Site",true,false, false)
  case object ErrorInShrine extends ResultStatus("Error In Shrine",1100,"Site Error", false, true, true)
  case object ErrorFromCrc extends ResultStatus("Error From CRC",1200,"Site Error",true,true, true)
  case object UnknownFinal extends ResultStatus("Unknown Final",1300,"Site Error",true,true, true)
  case object ResultFromCRC extends ResultStatus("Result From CRC",1400,"Completed",true,true, false)

  val statuses: Seq[ResultStatus] = Seq(
    IdAssigned,
    SentToAdapter,
    ReceivedByAdapter,
    QueuedByAdapter, //todo not currently used, but possible in the future when we do a better job handling the CRC's limited capacity
    ReadyToSubmit,
    ErrorInShrine,
    SubmittedToCRC,
    QueuedByCRC,
    UnknownWhileQueuedByCRC, //todo go deeper into just what the CRC does as part of SHRINE-2735
    UnknownInTransit,
    UnknownFinal,
    ErrorFromCrc,
    ResultFromCRC
  )

  lazy val namesToStatuses: Map[String, ResultStatus] = statuses.map(status => status.statusName -> status).toMap

}
package net.shrine.protocol.version

trait EnvelopeContents {
  def asJsonText:JsonText
  def protocolVersion:ProtocolVersion


}

trait EnvelopeContentsCompanion {
  def envelopeType: String //The fully qualified class name of what implements EnvelopeContents, like classOf[Result].getSimpleName

  //todo try to define tryRead here for SHRINE-2848
  //circe throws a "io.circe.DecodingFailure$$anon$2: CNil" with no stack trace. take advantage of that to log the json circe can't read
}
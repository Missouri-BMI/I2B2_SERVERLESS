package net.shrine.messagequeuemiddleware

import cats.effect.IO
import net.shrine.protocol.version.{Envelope, MomQueueName}
object MessageTranslationHandler {

  def translateMessage(toQueueName: MomQueueName, envelope: Envelope): IO[TranslatedMessage] = {
    MessageTranslationStrategy.getStrategy(envelope, toQueueName).flatMap(m =>
      m.fold(IO{val tm: TranslatedMessage = TranslatedMessage(toQueueName,envelope); tm})(e => e.translateMessage()))
  }
}

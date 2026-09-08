package net.shrine.http4s.client.legacy

/**
 * @author Bill Simons
 * @author clint
 *
 * @since Aug 3, 2010
 * @see http://cbmi.med.harvard.edu
 * @see http://chip.org
 * <p/>
 * NOTICE: This software comes with NO guarantees whatsoever and is
 * licensed as Lgpl Open Source
 * @see http://www.gnu.org/licenses/lgpl.html
 */
//todo may not need this interface. It's only used in mocking clients
trait HttpClient {
  def post(input: String, url: String): HttpResponse
}

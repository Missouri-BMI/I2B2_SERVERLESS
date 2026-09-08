package net.shrine

/**
 * Audit code for capturing ACT "metrics".
 *
 * @author david 
 * @since 1/27/20
 */
package object audit {

  type ShrineNodeId = String
  type UserName = String
  type LongQueryId = Long
  type QueryName = String
  type QueryNotes = String
  type QueryFaved = Boolean
  type Time = Long
  type Checksum = Long
  type AdapterName = String
}

package net.shrine.adapter.dao.model

import net.shrine.protocol.i2b2.query.I2b2QueryDefinition

import javax.xml.datatype.XMLGregorianCalendar

/**
 * @author clint
 * @since Oct 16, 2012
 */
//only used to store queries - can be deleted with the adapter's working database
final case class ShrineQuery(
                              id: Int,
                              i2b2MasterQueryId: String,
                              networkId: Long,
                              name: String,
                              username: String,
                              domain: String,
                              dateCreated: XMLGregorianCalendar,
                              queryDefinition: I2b2QueryDefinition)

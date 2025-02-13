/*
    Implements the data access layer (DAL).
    The data access layer generates and executes requests to the database.

    See the `mappings` module for the conversions between database rows and Kotlin objects.
 */

package io.pleo.antaeus.data

import io.pleo.antaeus.models.Customer
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.Money
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class InvoiceDal(private val db: Database) {
    fun fetchInvoice(id: Int): Invoice? {
        // transaction(db) runs the internal query as a new database transaction.
        return transaction(db) {
            // Returns the first invoice with matching id.
            InvoiceTable
                    .select { InvoiceTable.id.eq(id) }
                    .firstOrNull()
                    ?.toInvoice()
        }
    }

    fun fetchInvoices(): List<Invoice> {
        return transaction(db) {
            InvoiceTable
                    .selectAll()
                    .map { it.toInvoice() }
        }
    }

    fun fetchInvoicesByStatus(status: InvoiceStatus): List<Invoice> {
        return transaction(db) {
            InvoiceTable
                    .select(InvoiceTable.status.eq(status.name))
                    .map { it.toInvoice() }
        }
    }


    fun createInvoice(amount: Money, customer: Customer, status: InvoiceStatus = InvoiceStatus.PENDING): Invoice? {
        val id = transaction(db) {
            // Insert the invoice and returns its new id.
            InvoiceTable
                    .insert {
                        it[this.value] = amount.value
                        it[this.currency] = amount.currency.toString()
                        it[this.status] = status.toString()
                        it[this.customerId] = customer.id
                    } get InvoiceTable.id
        }

        return fetchInvoice(id)
    }

    fun updateInvoice(invoice: Invoice): Int {
        var result = 0
        transaction(db) {
            // Update the invoice and returns the result
            result = InvoiceTable
                    .update(where = { InvoiceTable.id.eq(invoice.id) }) {
                        it[this.value] = invoice.amount.value
                        it[this.currency] = invoice.amount.currency.toString()
                        it[this.status] = invoice.status.toString()
                        it[this.customerId] = invoice.customerId
                    }
        }

        return result
    }


}

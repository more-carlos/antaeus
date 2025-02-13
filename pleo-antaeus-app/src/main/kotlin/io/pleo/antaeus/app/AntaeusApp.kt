/*
    Defines the main() entry point of the app.
    Configures the database and sets up the REST web service.
 */

@file:JvmName("AntaeusApp")

package io.pleo.antaeus.app

import io.pleo.antaeus.core.external.billing.BillingInvoiceProducer
import io.pleo.antaeus.core.external.billing.BillingInvoiceConsumer
import io.pleo.antaeus.core.services.BillingService
import io.pleo.antaeus.core.services.CustomerService
import io.pleo.antaeus.core.services.InvoiceService
import io.pleo.antaeus.data.CustomerDal
import io.pleo.antaeus.data.CustomerTable
import io.pleo.antaeus.data.InvoiceDal
import io.pleo.antaeus.data.InvoiceTable
import io.pleo.antaeus.rest.AntaeusRest
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection

fun main() {
    // The tables to create in the database.
    val tables = arrayOf(InvoiceTable, CustomerTable)

    // Connect to the database and create the needed tables. Drop any existing data.
    val db = Database
        .connect(url = "jdbc:postgresql://db:5432/antaeus",
            driver = "org.postgresql.Driver",
            user = "antaeus",
            password = "secret")
        .also {
            TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
            transaction(it) {
                addLogger(StdOutSqlLogger)

                // Drop all existing tables to ensure a clean slate on each run
                SchemaUtils.drop(*tables)
                // Create all tables
                SchemaUtils.create(*tables)
            }
        }

    // Set up data access layer.
    val invoiceDal = InvoiceDal(db = db)
    val customerDal = CustomerDal(db = db)

    // Insert example data in the database.
    setupInitialData(invoiceDal = invoiceDal, customerDal = customerDal)

    // Get third parties
    val paymentProvider = getPaymentProvider()

    // Create core services
    val invoiceService = InvoiceService(dal = invoiceDal)
    val customerService = CustomerService(dal = customerDal)

    // Set up queue and start consumer
    val queueChannel = setupQueue()
    val billingServiceProducer = BillingInvoiceProducer(queueChannel)
    BillingInvoiceConsumer(queueChannel, paymentProvider,invoiceService).registerConsumer()


    // This is _your_ billing service to be included where you see fit
    val billingService = BillingService(
            invoiceService = invoiceService,
            billingInvoiceProducer = billingServiceProducer)

    // Create REST web service
    AntaeusRest(
        invoiceService = invoiceService,
        customerService = customerService,
        billingService = billingService
    ).run()
}

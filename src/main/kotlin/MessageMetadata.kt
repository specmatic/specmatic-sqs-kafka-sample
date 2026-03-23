package io.specmatic.async

import aws.sdk.kotlin.services.sqs.model.MessageAttributeValue
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import java.util.UUID

const val CORRELATION_ID_HEADER = "CorrelationId"

fun correlationIdFrom(record: ConsumerRecord<String, String>): String {
    return record.headers().lastHeader(CORRELATION_ID_HEADER)
        ?.value()
        ?.toString(Charsets.UTF_8)
        ?.takeIf { it.isNotBlank() }
        ?: UUID.randomUUID().toString()
}

fun addCorrelationIdHeader(record: ProducerRecord<String, String>, correlationId: String) {
    record.headers().remove(CORRELATION_ID_HEADER)
    record.headers().add(CORRELATION_ID_HEADER, correlationId.toByteArray(Charsets.UTF_8))
}

fun sqsCorrelationAttributes(correlationId: String): Map<String, MessageAttributeValue> {
    return mapOf(
        CORRELATION_ID_HEADER to MessageAttributeValue {
            dataType = "String"
            stringValue = correlationId
        }
    )
}

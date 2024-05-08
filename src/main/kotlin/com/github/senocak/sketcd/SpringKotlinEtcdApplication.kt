package com.github.senocak.sketcd

import com.fasterxml.jackson.databind.ObjectMapper
import io.etcd.jetcd.ByteSequence
import io.etcd.jetcd.Client
import io.etcd.jetcd.kv.DeleteResponse
import io.etcd.jetcd.kv.GetResponse
import io.etcd.jetcd.options.DeleteOption
import io.etcd.jetcd.options.GetOption
import java.nio.charset.Charset
import java.util.UUID
import java.util.concurrent.CompletableFuture
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Configuration
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Configuration
@ConfigurationProperties("spring.etcd")
class EtcdProperties {
    lateinit var host:String
    lateinit var user: String
    lateinit var password: String
}

data class User(
    var title: String,
    var author: String
) {
    var id: UUID? = null
}

@SpringBootApplication
@RestController
@RequestMapping("/etcd")
class SpringKotlinEtcdApplication(
    private val mapper: ObjectMapper,
    private val etcdProperties: EtcdProperties,
){
    private val log = LoggerFactory.getLogger(this::class.java.name)
    private val prefix = "Users"
    private val client = Client.builder()
        .endpoints(etcdProperties.host)
        .user(ByteSequence.from(etcdProperties.user, Charset.defaultCharset()))
        .password(ByteSequence.from(etcdProperties.password, Charset.defaultCharset()))
        .build()
    private val kvClient = client.kvClient

    @GetMapping
    fun findAll(): List<User> {
        val key = ByteSequence.from(prefix, Charset.defaultCharset())
        val option = GetOption.builder()
            .withSortField(GetOption.SortTarget.KEY)
            .withSortOrder(GetOption.SortOrder.DESCEND)
            .isPrefix(true)
            .build()
        val futureResponse: CompletableFuture<GetResponse> = kvClient.get(key, option)
        val response = futureResponse.get()
        if (response.kvs.isEmpty()) {
            log.warn("Failed to retrieve any user.")
            return ArrayList()
        }
        val users = arrayListOf<User>()
        response.kvs.forEach { kv ->
            users.add(element = mapper.readValue(kv.value.toString(), User::class.java))
        }
        log.info("Retrieved ${users.size} users.")
        return users
    }

    @PostMapping
    fun create(@RequestBody request: User): User = run {
        request.id = UUID.randomUUID()
        val key = getKey(id = request.id!!)
        kvClient.put(
            ByteSequence.from(key, Charset.defaultCharset()),
            ByteSequence.from(mapper.writeValueAsString(request), Charset.defaultCharset())
        ).get()
        log.info("Created new resource: $request")
        return request
    }

    @GetMapping("/{id}")
    fun findOne(@PathVariable id: UUID): User? {
        val key = getKey(id)
        val futureResponse: CompletableFuture<GetResponse> =
            kvClient.get(ByteSequence.from(key, Charset.defaultCharset()))

        val response = futureResponse.get()
        if (response.kvs.isEmpty()) {
            log.warn("Failed to retrieve any user with ID $id")
            return null
        }
        log.info("Retrieved user with ID $id")
        return mapper.readValue(response.kvs[0].value.toString(), User::class.java)
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID) {
        val key = getKey(id = id)
        val response: DeleteResponse = kvClient.delete(ByteSequence.from(key, Charset.defaultCharset())).get()
        if (response.deleted == 1L) {
            log.warn("Deleted user with ID $id")
            return
        }
        throw RuntimeException("Failed to delete user with ID $id")
    }

    @DeleteMapping
    fun deleteAll(): Long {
        val key = ByteSequence.from(prefix, Charset.defaultCharset())
        val deleteOpt = DeleteOption.builder().isPrefix(true).build()
        val response: DeleteResponse = kvClient.delete(key, deleteOpt).get()
        log.info("Deleted ${response.deleted} number of users.")
        return response.deleted
    }

    @GetMapping("/keys")
    fun findAllKeys(): Map<String, String>? {
        val key = ByteSequence.from("\u0000", Charset.defaultCharset())
        val option = GetOption.builder()
            .withSortField(GetOption.SortTarget.KEY)
            .withSortOrder(GetOption.SortOrder.DESCEND)
            .withRange(key)
            .build()
        val futureResponse: CompletableFuture<GetResponse> = kvClient.get(key, option)
        val response = futureResponse.get()
        if (response.kvs.isEmpty()) {
            log.warn("Failed to retrieve any keys.")
            return null
        }
        val keyValueMap: MutableMap<String, String> = HashMap()
        response.kvs.forEach { kv ->
            keyValueMap[kv.key.toString()] = kv.value.toString()
        }
        log.info("Retrieved ${keyValueMap.size} keys.")
        return keyValueMap
    }

    private fun getKey(id: UUID): String = prefix + id.toString()
}

fun main(args: Array<String>) {
    runApplication<SpringKotlinEtcdApplication>(*args)
}
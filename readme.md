# CRUD Operation for ETCD Key-Value DataStore

### Get All Data
```http request
GET http://127.0.0.1:8091/etcd
```

### Get By Id
```http request
GET http://127.0.0.1:8091/etcd/5974ab1c-bbfe-43b3-a689-623a29ad15d7
```

### Get All Keys
```http request
GET http://127.0.0.1:8091/etcd/keys
```

### Create New Data
```http request
POST http://127.0.0.1:8091/etcd
Content-Type: application/json

{
"title": "{{$random.alphanumeric(10)}}",
"author": "{{$random.email}}"
}
```

### Delete All Data
```http request
DELETE http://127.0.0.1:8091/etcd
```

### Delete Single Data
```http request
DELETE http://127.0.0.1:8091/etcd/cd45f051-0378-413a-89ed-49d3cf90e9a8
```
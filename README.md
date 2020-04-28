# Trustable-Anonymous-Voting
# Distributed Ledger

Please read the writeup and the API documents for a description of the project.


## How to run tests

1. Modify necessary fields in `test/Config.java`.

2. Run checkpoint tests:
```
make checkpoint
```

3. Run final tests:
```
make test
```

4. Run blockchain speed test:
```
make speed
```

## Handy tool to test server connection
```
# command format:
curl -v -X POST localhost:7001/getchain -d '{JSON}'
# for example:
curl -v -X POST localhost:7001/getchain -d '{"chain_id":1}'
```

## TO improve this project further

1. It took us a week approximately to finish the whole project (including checkpoint). The load is not that high 
<<<<<<< HEAD
compared to the previous project. 
=======
    compared to the previous project. Very good projects as previous projects are. Learned a bunch from these
>>>>>>> more comments added

2. Nothing to say specifically. TAs are the best. Thank them for their hard work and help. 
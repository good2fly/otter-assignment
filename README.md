# README

Author: `Attila Uhljar`

## How to run

The `Dockerfile` defines a self-contained Java/Gradle reference environment.
Build and run the program using [Docker](https://docs.docker.com/get-started/get-docker/):
```
$ docker build -t challenge .
$ docker run --rm -it challenge --auth=<token>
```

If java `25` or later is installed locally, run the program directly for convenience:
```
$ ./gradlew run --args="--auth=<token>"
```

## Discard criteria

Since we know that expired orders (orders that exceed their 'freshness' limit) are discarded no matter what, the primary
condition for selecting an order to be discarded is their expiration time. We are not losing any income by discarding 
already expired orders, and minimize potential loss by discarding the orders that are soonest to expire if there are no
already expired orders available. I chose to then break the tie by discarding the less expensive order (this is only
really relevant if the oldest orders haven't expired yet).

## Design notes
- The order processing time is very small and does not involve long, blocking operations, so 
  the actual processing is single-threaded, but thread-safe, as the harness itself can be run concurrently
- Since the business logic (especially the placement) involves many steps and has to maintain invariants across
  several internal state variables, I opted for global locks, rather than try to complicate things by
  trying to make locking fine-grained (and likely much more complicated)
- I opted for basing the ordering/discard logic on expiration time, rather than 'freshness', as 
  1. It is a lot easier to reason about
  2. Directly comparable to each other
- Expiration time can be calculated at most twice per order:
  1. When the order is placed. This is based purely on 'freshness', and whether the order is placed in its ideal storage
  2. When the order is moved from shelf to its ideal storage. We have to account for the time it spent in the non-ideal 
     storage (see more details in the Javadoc).
- In general, storages are ordered primarily by expiration time for discarding purposes (see above),
  then by price (for tie-breaker), then by order ID to maintain stable ordering
- Store shelf items in sub-storages (one for each temp) to simplify and speed up moving hot/cold orders
  from the shelf to their ideal storage. 
- Since the problem description does not explicitly state what an action 'target' means for pickup and discard actions, 
  I opted for the following:
  - For discard, the 'target' is the storage the order is discarded from
  - For pickup, the 'target' is the storage the order is picked up from

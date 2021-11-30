# re-fancoil
A proof-of-concept exercise, deconstruct [re-frame][] first, then refactor it with [integrant][]. 

[re-frame]:https://github.com/day8/re-frame
[integrant]:https://github.com/weavejester/integrant

## The ideal system structure
* **Parts**: parts carry new features. Parts are immutable data and functions.
* **Machine**: machines are built up from parts and have a state (atom) that changes over time in production.
* **System**: system is responsible for coordinating multiple machines, use configuration file to start all machines in order of dependency.

## Problems with re-frame
Hidden atom in the system: mainly registrar's kind->id->handler, subs' query->reaction, db's app-db
Creating a system with hard-coded reg processes: mainly reg-sub, reg-event-*, reg-fx, reg-cofx, etc.

## Principles of refactoring
* Correct dependencies: parts (value, fn) -> machine (atom, async) -> system (integrant). The top layer depends on the bottom layer, each layer will depend on its own related libraries
* More parts, simple system integration: because machines and systems are mutable
* Transparent system structure, flexible machine configuration

## Description of re-fancoil

Pulled out the hidden atom, put it inside the integrant init-key function, and inject it to other machines after system startup

Parts include input-fn and handler-fn for subscribe machine, and interceptor and handler for event machine, reagent-view-fn, registered to registrar at startup.

Added keyword interceptor to load interceptors already registered in registrar.

Changed input-fn for subscribe,  query-vector -> dependent query-vectors, no longer dependency on previous subscribe function

Only part of code have been refactored, logger, trace, setting, are still global variables unchanged.

Framework code in re-fancoil folder, and example code in simple-re-fancoil and todomvc-re-fancoil folder

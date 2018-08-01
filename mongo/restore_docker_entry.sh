#!/bin/bash
/entrypoint.sh mongod &
if [ $? == 0 ]; then
  echo -e "\nRestore start"
  mongorestore --oplogReplay --dir=/week_$1 --drop --maintainInsertionOrder --stopOnError
  if [ $? != 0 ]; then
    echo -e "\nRestore process error" >&2
  else
    echo -e "\n\nRestore is finished.\n\n"
    for node in ${nodes[@]}; do
      echo "DB, collections, documents number in ${node}  "
      mongo --eval "
      rs.slaveOk();
      db.adminCommand('listDatabases').databases.forEach(function(d){
        let mdb = db.getSiblingDB(d.name);
        mdb.getCollectionInfos().forEach(function(c){
          let cc = mdb.getCollection(c.name);
          cc.reIndex()
          printjson(  mdb + '.' + c.name + ': ' + cc.count() + ' validate result: '+ cc.validate(true).ok);
        });
      }); "
      echo "-----------------------------"
    done
    mongo
  fi
else
  echo -e "\nFailed to start mongod " >&2
fi

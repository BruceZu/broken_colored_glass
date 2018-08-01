/*
 Support mongo v3.4.4
 with lock to get the oplog timestamp of point-in-time backup used for making incremental backup
 with '--oplog' as a way to check is the lock is unlocked by other
 with '--host localhost' to dumped 0 oplog entries. mongodump may has a bug with this default option value. 
/*

/* Checks if server is currently locked and returns true or false */
isLocked = function () {
    const lockCheck = db.currentOp();
    if (lockCheck.hasOwnProperty("fsyncLock") && lockCheck.fsyncLock) return true;
    else return false;
}

rs.slaveOk();
if (isLocked()) {
    print("\nWARN: server is already locked by other\n");
}

/* Check that if this is in a replica set and is a secondary */
let isMaster = db.isMaster();
if (isMaster.hasOwnProperty("setName") && isMaster.ismaster) {
    const message = "ERROR: This member became master, backup should be done on a secondary!";
    print("\n" + message);
    throw message;
}
print("\nSecondary member");

const db = db.getSiblingDB('local');
let timeStampOfLatestOplog = db.oplog.rs.find().sort({ ts: -1 }).limit(1).next().ts;
print("\nBefore lock, the current oplog timestamp *1*" + timeStampOfLatestOplog + "\n");

/* Lock the server, check success */
const lockResult = db.fsyncLock();
if (lockResult.ok != 1) {
    const message = "ERROR: Didn’t successfully lock the mongod. Returned status is " + lockResult.code + " " + lockResult.errmsg;
    print("\n" + message);
    throw message;
} else {
    print("\nMongod locked \n");
}

timeStampOfLatestOplog = db.oplog.rs.find().sort({ ts: -1 }).limit(1).next().ts;
print("\nAfter lock, the most recent oplog timestamp *2*" + timeStampOfLatestOplog + "\n");

/* Full backup.  Assume enough spaces */
let week = ISODate().getDay(); // same as bash: date +%u only the sun is 0 not 7
if (week == 0) {
    week = 7;
}
const path = "/data/backup/week_" + week;
const command = "mongodump --host localhost --out " + path + " --oplog";
setVerboseShell(true);
print("\nStart " + ObjectId().getTimestamp());
let shellResult = run("/bin/bash", "-c", command);
print("\nEnd " + ObjectId().getTimestamp());

if (shellResult != 0) {
    print("\nERROR: Failed to run the backup command. Returned status " + shellResult);
} else {
    const file = path + "/oplog.bson";
    const testCommand = "a=`file -b  /data/backup/week_4/oplog.bson`; if [[ $a != empty ]] ; then echo 1; else echo 0; fi"
    shellResult = run("/bin/bash", "-c", testCommand);
    if (shellResult != 0) {
        print("\nWARN: dumped >0 oplog entries. Somebody else may unlocked the mongod during backup. Try delete the  " + file);
        removeFile(file);
        print("\nRemoved " + file);
    } else {
        print("\nBackup Success \n");
        timeStampOfLatestOplog = db.oplog.rs.find().sort({ ts: -1 }).limit(1).next().ts;
        print("\nAfter backup, the most recent oplog timestamp *3*" + timeStampOfLatestOplog + "\n");
    }
}

/*Always unLock regardless of success of backup! */
db.printSlaveReplicationInfo()
print("\nTry unlock mongod\n");
let unlockResult = db.fsyncUnlock();
if (unlockResult.ok != 1) {
    const noLockMessage = "fsyncUnlock called when not locked";
    if (errmsg == noLockMessage) {
        print("\nWARN: unlocked by other! the recoreded recent optlog ts maybe invalid, try to use the one before locked");
    } else {
        print("\nERROR: Failed to unlock. Returned status: " + tojson(unlockResult));
        print("\t try again every 5 seconds!");
        while (unlockResult.ok != 1) {
            if (unlockResult.errmsg == noLockMessage) {
                print("\tWARN: Unlocked by other and NO any lock now");
                break;
            }
            sleep(5000);
            unlockResult = db.fsyncUnlock();
        }
    }
}
print("\nUnlocked\n");
db.printSlaveReplicationInfo();
printjson(listFiles(path));

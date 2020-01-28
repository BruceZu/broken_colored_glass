[TOC]

## On-line upgrading multi Application instances sharing one DB

### Target

- Without stopping all Application instances
- without getting the DB data dirty.
- Application is available during the upgrading process

### Application instances scenario

- 2 or more Application instances sharing one DB service/instance.
- To make it simple the 'DB' in this article will be one DB instance not one DB service.
- The DB in the following cases is assumed as Maria DB or MySql

### Old solution

(A temporary solution discussed with Hai in a very limited time)
Stop any DB operation if 'version diff' is found.
The 'version diff' here means Application code version is not same as Application DB version.
E.g. Application code Rel_6.0 is found running against Application DB Rel_7.0.

#### Pros

#### Cons

- Not easy to carry out: Because it requires the back end code to check the 'version diff' before executing any DB CURD operation, and it needs to keep the 'version diff' checking in the same transaction with any DB CURD operation. Because DB upgrading can happen at anytime.
- Not easy to maintain. It is very easy for developers to forget adding the checking logic in new code.
- During the whole process of upgrading, most functions of the old version Application instance(s) are not available , thus 'on-line upgrading' loses its meaning. Because most requests need touch DB. E.g. save authentication log.

### Proposal Solution

The existing of 'version diff' does not mean all DB data definition or data provided by Application are changed, instead most of them are same as the previous version. Actually what we only care are those change on DB data definition[1] or data provided by Application and the CURD operations against them.

Assume: The load balance can always monitor Application instance status and forward user requests to a alive Application instance.

#### Idea

- For a give Application instance the upgrading may include multiple steps. Each step with a intermediate version. The number of steps depends on concrete upgrading scenario.
- Each Application intermediate version supports previous and subsequent version from both code CRUD operation and DB.
- Each Application DB version upgrading needs to execute only once. This can be achieved by checking the DB version in advance of execution. Using idempotent SQL statement is not must required even it can help developer executing SQL repeatedly in development phrase to debug data update.

#### Pros

- End user: It is completely on-line upgrading . All Application old features are available during the process.
- Developer need not check 'version diff', make development and maintain work easier.
- Application administrator can start upgrading Application at any time point with any length of time. need not schedule it at half night, weekend or in a limited time window.
- This idea and experience can be applied to Cloud scenario in future.

#### Cons

- For a give Application instance, upgrading may need more steps. This will add the work of QA , Application admin and back end developer who need change the DB data definition or data provided by Application. This is the cost of on-line upgrading.

#### Example Scenario

There are 3 Application instances Aa, Ab, Ac and 1 DB instance. All are of version 6.0 before upgrading to version 7

```
| ------- | ------- | ------- | ------- |
| Aa(6.0) | Ab(6.0) | Ac(6.0) | DB(6.0) |
| ------- | ------- | ------- | ------- |
```

There is a table 'car' in DB, 'car' has a column 'num' with type 'int(11)'， other columns are ignored for simple.

```sql
mysql> desc car;
+-------+---------+------+-----+---------+-------+
| Field | Type    | Null | Key | Default | Extra |
+-------+---------+------+-----+---------+-------+
| num   | int(11) | YES  |     | NULL    |       |
+-------+---------+------+-----+---------+-------+
```

There are some existing data or records:

```sql
mysql> select * from car;
+-------+
| num   |
+-------+
| 10101 |
| 20102 |
+-------+
```

#### Example: Update column type

Old feature: The car number is only composed of number. E.g. '10101'
New feature: Enable 'car.num' support char. E.g '5abc5'

This requires to modify the type of 'car.num' from 'int(11)' to 'varchar(11)'

For making the new solution easier to understand, let's compare it with current behavior. If there is only one Application(6.0) instance and off-line upgrading is allowed, the upgrading steps will be:
a> Notice end user about the Application off-line upgrading time window.
b> In the upgrading time window: stop Application(6.0), then upgrading Application Rel_7 image which will do:
-- run the following DB migration SQL statements to upgrading DB(6.0) to DB(7)
-- start up the Application(7)

```sql
mysql> alter table car modify num varchar(11);
Query OK, 2 rows affected (0.09 sec)
Records: 2  Duplicates: 0  Warnings: 0

mysql> desc car;
+-------+-------------+------+-----+---------+-------+
| Field | Type        | Null | Key | Default | Extra |
+-------+-------------+------+-----+---------+-------+
| num   | varchar(11) | YES  |     | NULL    |       |
+-------+-------------+------+-----+---------+-------+
1 row in set (0.01 sec)
```

Not let's see the proposal on-line upgrading solution steps in above described scenario: 3 Application instances and 1 DB instance

##### Step 1-1: Stop Aa(6.0), then upgrade the Aa with Application image Rel_7_d_1.

Let's understand 'Rel_7_d_1' as Release version 7 at deployment step 1.

Application image 'Rel_7_d_1' deployment will do:
a> Run the following DB migration SQL statements to upgrading DB(6.0) to DB(7_d_1)

```sql
alter table car add column plate_num varchar(11);
update car set plate_num=convert(num,char(11));
```

b> Start up the Aa(7_d_1):

Aa(7_d_1)
The CRUD operations against table 'car' will need comply with the rule of
"Application intermediate version supports previous and subsequent version from both code CRUD operation and DB."
Let's see the diff of CRUD operation code between Ab(6.0), Ac(6.0) and Aa(7_d_1) from the SQL statement view with the following cases:

_Create Record_:
Ab(6.0), Ac(6.0):

```sql
insert into  car (num) VALUES (30103);
```

Aa(7_d_1):

```sql
insert into car (num, plate_num) VALUES (40104,'40104');
```

```sql
insert into car (plate_num) VALUES ( '5abc4');
```

_Read Record_:
Ab(6.0), Ac(6.0)

```sql
SELECT * from car where num=40104
```

Aa(7_d_1):

```sql
# in one transaction
update car set plate_num=convert(num,char(11)) where num is not null;
SELECT num, plate_num from car where plate_num='40104'
```

```sql
update car set plate_num=convert(num,char(11)) where num is not null;
SELECT num, plate_num from car where plate_num='5abc4'
```

Why is there a UPDATE before SELECT?
Ab(6.0) and Ac(6.0) can insert or update 'num' at anytime

Which value should be used?

```java
String num_value = ObjectUtils.firstNonNull(num, plate_num);
```

_Update Record_:
Ab(6.0), Ac(6.0)

```sql
update car set num=30123 where num=30103;
```

Aa(7_d_1):

```sql
update car set num=30103, plate_num='30103'
where (num=30123 or num is null) and plate_num='30123';
```

Why is the restriction applied on both num and plate_num?
-- Avoid covering the UPDATE made by Ab(6.0) or Ac(6.0), the UPDATE can happen after the read of Aa(7_d_1) and its current update.
Why is there restriction like "num is null and plate_num='30123'";
-- Be compatible with the record created by Rel_7_d_2 in the next step. remember the rule
"Application intermediate version supports previous and subsequent version from both code CRUD operation and DB."
It is the same reason for updating both column 'num' and 'plate_num' when the value is number.

Developer need check the updated rows number. If the number is 0 in the above case,
Notice current user the record to be updated is changed after the time current-user read it.

```sql
update car set plate_num='5abc5' where plate_num='5abc4'
```

_Delete Record_:
Ab(6.0), Ac(6.0)

```sql
delete from car where num=10101;

```

Aa(7_d_1):

```sql
delete from car where plate_num='5abc5';
```

```sql
delete from car
where (num=30123 or num is null) and plate_num='30123';
```

Note at present:

- The Ab(6.0) and Ac(6.0) keep alive to provide old features. also they can RUD operation on the records that is created by Aa(7_d_1) and belongs to old feature scope.
- Aa(7_d_1) start providing new feature and at the same time it compatible the old feature.
  All of 3 Application instances alive harmoniously together without conflict and without getting data dirty.

After step 1-1 is finished the scenario status becomes:

```
| ------- | ----------- | ------- | ------- | ----------- |
| initial | Aa(6.0)     | Ab(6.0) | Ac(6.0) | DB(6.0)     |
| ------- | ----------- | ------- | ------- | ----------- |
| step 1-1| Aa(7_d_1)   | Ab(6.0) | Ac(6.0) | DB(7_d_1)   | <-- now

```

##### Step 1-2: Stop Ab(6.0), then upgrade the Ab with Application image Rel_7_d_1.

'Each Application DB version upgrading needs to execute only once'. So no touch on DB DB(7_d_1) in this step.

##### Step 1-3: Stop Ac(6.0), then upgrade the Ac with Application image Rel_7_d_1.

```
| ------- | ----------- | ----------- | ----------- | ----------- |
| initial | Aa(6.0)     | Ab(6.0)     | Ac(6.0)     | DB(6.0)     |
| ------- | ----------- | ----------- | ----------- | ----------- |
| step 1-1| Aa(7_d_1)   | Ab(6.0)     | Ac(6.0)     | DB(7_d_1)   |
| step 1-2| Aa(7_d_1)   | Aa(7_d_1)   | Aa(6.0)     | DB(7_d_1)   |
| step 1-3| Aa(7_d_1)   | Aa(7_d_1)   | Aa(7_d_1)   | DB(7_d_1)   | <-- now

```

Once step 1-3 is done, old version is not available. New feature is available from all 3 instances.
So the following kind of record will not created anymore, while this kind of records may still exist in DB.

```sql
+-------+-----------+
| num   | plate_num |
+-------+-----------+
| 30103 | NULL      |
```

Step 2 with Application image Rel_7_d_2 will make the code and records easy maintain in future by not using the column 'num' . The code in Application image Rel_7_d_2 is the targeted code version.

##### Step 2-1: Stop Aa(7_d_1), then upgrade the Aa with Application image Rel_7_d_2.

Let's understand 'Rel_7_d_2' as Release version 7 at deployment step 2.

Application image 'Rel_7_d_2' deployment will do:
a> Run the following DB migration SQL statements to upgrading DB(7_d_1) to DB(7_d_2)

```sql
update car set plate_num=convert(num,char(11)) where num is not null;
```

b> Start up the Aa(7_d_2):

Aa(7_d_2)
The CRUD operations against table 'car' will need comply with the rule of
"Application intermediate version supports previous and subsequent version from both code CRUD operation and DB."
Let's see the diff of CRUD operation code between Ab(7_d_1), Ac(7_d_1) and Aa(7_d_2) from the SQL statement view with the following cases:

_Create Record_:
Ab(7_d_1), Ac(7_d_1):

```sql
insert into car (num, plate_num) VALUES (40104,'40104');
```

```sql
insert into car (plate_num) VALUES ('5abc4');
```

Aa(7_d_2):

```sql
insert into car (plate_num) VALUES ('60106');
```

```sql
insert into car (plate_num) VALUES ('7abc7');
```

_Read Record_:
Ab(7_d_1), Ac(7_d_1):

```sql
# in one transaction
update car set plate_num=convert(num,char(11)) where num is not null;
SELECT num, plate_num from car where plate_num='40104'
```

```sql
update car set plate_num=convert(num,char(11)) where num is not null;
SELECT num, plate_num from car where plate_num='5abc4'
```

```java
String num_value = ObjectUtils.firstNonNull(num, plate_num);
```

Aa(7_d_2):

```sql
SELECT plate_num from car where plate_num='60106'
```

```sql
SELECT plate_num from car where plate_num='7abc7'
```

_Update Record_:
Ab(7_d_1), Ac(7_d_1):

```sql
update car set num=30103, plate_num='30103'
where (num=30123 or num is null) and plate_num='30123';
```

```sql
update car set plate_num='5abc5' where plate_num='5abc4'
```

Aa(7_d_2):

```sql
update car set plate_num='65556' where plate_num='60106'
```

```sql
update car set plate_num='abc77' where plate_num='7abc7'
```

_Delete Record_:
Ab(7_d_1), Ac(7_d_1):

```sql
delete from car where plate_num='5abc5';
```

```sql
delete from car
where (num=30123 or num is null) and plate_num='30123';
```

Aa(7_d_2):

```sql
delete from car where plate_num='65556';
```

```sql
delete from car where plate_num='abc77';
```

Note at present:

- The Ab(7_d_1) and Ac(7_d_1) keep alive using column 'num'. Both of them are compatible with Aa(7_d_2). Aa(7_d_2) only uses column 'plate_num'. All of 3 Application instances alive harmoniously together without conflict without getting data dirty.
- DB(7_d_2) and DB(7_d_1) has not different data definition. Only records in DB are not same.

After step 2-1 is finished the scenario status becomes:

```
| ------- | --------- | --------- | --------- | --------- |
| step 1-3| Aa(7_d_1) | Ab(7_d_1) | Ac(7_d_1) | DB(7_d_1) |
| ------- | --------- | --------- | --------- | --------- |
| step 2-1| Aa(7_d_2) | Ab(7_d_1) | Ac(7_d_1) | DB(7_d_2) | <-- now

```

##### Step 2-2: Stop Ab(7_d_1), then upgrade the Ab with Application image Rel_7_d_2.

'Each Application DB version upgrading needs to execute only once'. So no touch on DB DB(7_d_2) in this step.

##### Step 2-3: Stop Ac(7_d_1), then upgrade the Ac with Application image Rel_7_d_2.

```
| ------- | --------- | --------- | --------- | --------- |
| step 1-3| Aa(7_d_1) | Ab(7_d_1) | Ac(7_d_1) | DB(7_d_1) |
| ------- | --------- | --------- | --------- | --------- |
| step 2-1| Aa(7_d_2) | Ab(7_d_1) | Ac(7_d_1) | DB(7_d_2) |
| step 2-2| Aa(7_d_2) | Ab(7_d_2) | Ac(7_d_1) | DB(7_d_2) |
| step 2-3| Aa(7_d_2) | Ab(7_d_2) | Ac(7_d_2) | DB(7_d_2) | <-- now

```

Once step 2-3 is done, all 3 instances are running Rel_7_d_2. the column 'num' is not used anymore.
So the last step 3 is to drop the column 'num'.

##### Step 3-1: Stop Aa(7_d_2), then upgrade the Aa with Application image 'Rel_7'

Application image 'Rel_7' is the last image. Its deployment will do:
a> Run the following DB migration SQL statements to upgrading DB(7_d_2) to DB(7)

```sql
alter table car drop column num;
```

b> Start up the Aa(7):
Application code version of 'Rel_7' is completely same as that of 'Rel_7_d_2'.

##### Step 3-2: Stop Ab(7_d_2), then upgrade the Ab with Application image Rel_7.

'Each Application DB version upgrading needs to execute only once'. So no touch on DB DB(7) in this step.

##### Step 3-3: Stop Ac(7_d_2), then upgrade the Ac with Application image Rel_7.

```
| ------- | --------- | --------- | --------- | --------- |
| step 2-3| Aa(7_d_2) | Ab(7_d_2) | Ac(7_d_2) | DB(7_d_2) |
| ------- | --------- | --------- | --------- | --------- |
| step 3-1| Aa(7)     | Ab(7_d_2) | Ac(7_d_2) | DB(7)     |
| step 3-2| Aa(7)     | Ab(7)     | Ac(7_d_2) | DB(7)     |
| step 3-3| Aa(7)     | Ab(7)     | Ac(7)     | DB(7)     | <-- now
```

##### Summary

Prepare 3 Application VM images: Rel_7_d_1, Rel_7_d_2 and Rel_7.

#### Other examples

Compared to the above 'Updating column type' the solution for other data definition change scenario[1] are easier to figure out with the ideas of the proposal solution. So skipped here.
Table data/records change scenarios[3], add/delete/update record, can be implemented by adding a column marking the record's valid version scopes. Not discussed here too.

Welcome QA.

[1] Maria DB Data Definition https://mariadb.com/kb/en/data-definition/
[2] Maria DB Alter table syntax https://mariadb.com/kb/en/alter-table/
[3] Maria DB Data Manipulation https://mariadb.com/kb/en/data-manipulation/

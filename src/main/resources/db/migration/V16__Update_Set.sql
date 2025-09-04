ALTER TABLE matching
DROP COLUMN matching_status;

create table matching_queue
(
    matching_queue_id bigint auto_increment
        primary key,
    created_at        datetime(6)                  null,
    updated_at        datetime(6)                  null,
    gender            enum ('FEMALE', 'MALE')      not null,
    group_id          varchar(255)                 not null,
    matching_status   enum ('COMPLETE', 'WAITING') not null,
    user_id           bigint                       null,
    constraint FK9lc16hj0uvbw7ijolev9ko6uo
        foreign key (user_id) references user (user_id)
);

ALTER TABLE Club MODIFY COLUMN category ENUM('DATE', 'FOOD', 'GOODS', 'EVENT', 'OTHERS');
ALTER TABLE Club MODIFY COLUMN place ENUM('S_LEFT', 'S_RIGHT', 'A', 'F', 'S', 'K', 'G');
ALTER TABLE club
    MODIFY COLUMN rep varchar(255) NULL,
    MODIFY COLUMN time varchar(255) NULL,
    MODIFY COLUMN info TEXT NULL;

ALTER TABLE item
    MODIFY COLUMN name varchar(255) NULL,
    MODIFY COLUMN content varchar(255) NULL;

-- 1. chat_room 테이블 변경 (name 컬럼 삭제)
ALTER TABLE chat_room
DROP COLUMN name;

-- 2. team_chat_room 테이블 추가
CREATE TABLE team_chat_room
(
    join_chat_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at   DATETIME(6) NULL,
    updated_at   DATETIME(6) NULL,
    name         VARCHAR(255) NOT NULL,
    chat_room_id BIGINT NULL,
    team_id      BIGINT NULL,
    CONSTRAINT FK31q27sat6p559stmwnsrtcc6h
        FOREIGN KEY (chat_room_id) REFERENCES chat_room (chat_room_id),
    CONSTRAINT FKf3sy2c7oagi832n5x022w6y5n
        FOREIGN KEY (team_id) REFERENCES team (team_id)
);

-- refresh_token 테이블에 user_id 컬럼 추가
ALTER TABLE refresh_token
    ADD COLUMN user_id BIGINT NULL;

-- refresh_token 테이블의 user_id에 외래 키 제약 조건 추가
ALTER TABLE refresh_token
    ADD CONSTRAINT FK_refresh_token_user
        FOREIGN KEY (user_id) REFERENCES user (user_id)
            ON DELETE CASCADE;

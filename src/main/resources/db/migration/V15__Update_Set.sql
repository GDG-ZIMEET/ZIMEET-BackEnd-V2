ALTER TABLE fcm_token
DROP FOREIGN KEY FKe0a0mkcsnkfse7c7pljt8kbn3,
    CHANGE COLUMN user_user_id user_id BIGINT NOT NULL,
    ADD CONSTRAINT UC_user_id UNIQUE (user_id),
    ADD CONSTRAINT fk_user_id FOREIGN KEY (user_id) REFERENCES user(user_id),
    MODIFY token VARCHAR(255) NOT NULL;
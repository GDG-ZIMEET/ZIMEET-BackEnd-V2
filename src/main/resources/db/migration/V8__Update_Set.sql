--  기존 password 컬럼의 UNIQUE 제약 조건 제거
ALTER TABLE user DROP INDEX UKkiqfjabx9puw3p1eg7kily8kg;

--  phone_number 컬럼에 UNIQUE 제약 조건 추가
ALTER TABLE user ADD CONSTRAINT UK_phone_number UNIQUE (phone_number);

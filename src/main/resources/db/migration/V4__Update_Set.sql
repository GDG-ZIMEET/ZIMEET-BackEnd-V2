-- 1. `hi_status` 컬럼 추가 (hi 테이블)
ALTER TABLE hi
    ADD COLUMN hi_status ENUM('ACCEPT', 'NONE', 'REFUSE') NOT NULL;

-- 2. `phone_number` 컬럼 추가 (user 테이블)
ALTER TABLE user
    ADD COLUMN phone_number VARCHAR(255) NOT NULL;

-- 3. `delete_team` → `left_delete` 컬럼명 변경 (user_profile 테이블)
ALTER TABLE user_profile
    CHANGE COLUMN delete_team left_delete INT NOT NULL;

-- 4. `major` ENUM 값 업데이트 (user_profile 테이블)
ALTER TABLE user_profile
    MODIFY COLUMN major ENUM(
    'ACCOUNTING', 'AI', 'BIO', 'BIOTECH', 'BMCE', 'BMSW', 'BUSINESS',
    'CHEMISTRY', 'CHILDREN', 'CHINESE', 'CLOTHING', 'CSIE', 'DA', 'DESIGN',
    'ECONOMICS', 'ENGLISH', 'ENVI', 'FOOD_NUTRITION', 'FRENCH', 'GBS',
    'HUMANITIES', 'HUMAN_SOCIAL', 'ICE', 'ICT', 'INTERNATIONAL', 'INTER_LAW',
    'JAPANESE', 'KOREAN', 'KOREANHISTORY', 'LANGUAGE', 'LAW', 'LIBREAL',
    'LIFE_SCIENCE', 'MANAGEMENT', 'MATH', 'MEDICAL_BIOLOGY', 'MEDICINE',
    'MTC', 'MUSIC', 'NATURAL_ENGINEERING', 'NATURAL_SCIENCE', 'NURSING',
    'PHARMACY', 'PHILOSOPHY', 'PHYSICS', 'PSYCHOLOGY', 'PUBLIC_ADMINISTRATION',
    'SOCIALWELFARE', 'SOCIAL_SCIENCE', 'SOCIOLOGY', 'SPECIAL_EDUCATION', 'THEOLOGY'
    ) NOT NULL;

-- 5. `joined_at` 컬럼 삭제 (join_chat 테이블)
ALTER TABLE join_chat
DROP COLUMN joined_at;


-- UserProfile 테이블에 ticket 컬럼 추가
ALTER TABLE user_profile
    ADD COLUMN ticket INT NOT NULL DEFAULT 2;



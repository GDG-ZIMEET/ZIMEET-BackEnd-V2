import sys

sql = []
sql.append("USE zimeet;")
sql.append("SET FOREIGN_KEY_CHECKS = 0;")
sql.append("TRUNCATE TABLE fcm_token;")
sql.append("TRUNCATE TABLE `user`;")
sql.append("SET FOREIGN_KEY_CHECKS = 1;")

# 유저 1000명 생성
user_values = []
token_values = []

for i in range(1, 1001):
    student_num = f"2026{str(i).zfill(4)}"
    phone_num = f"010-0000-{str(i).zfill(4)}"
    # password, name, phone_number, is_deleted, push_agree, fcm_send_two_two
    user_values.append(f"({i}, '{student_num}', 'hashed_password', 'Mock User {i}', '{phone_num}', 0, 1, 0, NOW(), NOW())")
    
    # 각 유저당 FCM 토큰 1개 생성
    mock_fcm = f"mockToken_{i}_{student_num}"
    token_values.append(f"({i}, {i}, '{mock_fcm}', NOW(), NOW())")

# Bulk Insert 쿼리 생성
user_sql = "INSERT INTO `user` (user_id, student_number, password, name, phone_number, is_deleted, push_agree, fcm_send_two_two, created_at, updated_at) VALUES\n"
user_sql += ",\n".join(user_values) + ";"

token_sql = "INSERT INTO fcm_token (id, user_id, token, created_at, updated_at) VALUES\n"
token_sql += ",\n".join(token_values) + ";"

sql.append(user_sql)
sql.append(token_sql)

with open("mock_data.sql", "w") as f:
    f.write("\n".join(sql))
    
print("mock_data.sql generation complete.")

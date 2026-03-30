import socket
import time
import sys

# 서버 호스트와 포트 설정
HOST = sys.argv[1] if len(sys.argv) > 1 else 'backend-blue'
PORT = 8080

def send_slow_request():
    try:
        print(f"[slow_sender] {HOST}:{PORT} 에 연결 중...")
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        # 타임아웃 5초 설정
        s.settimeout(5)
        s.connect((HOST, PORT))
        print("[slow_sender] 연결 성공!")

        # 1. 결제 API 엔드포인트 헤더 전송 (Wireshark에서 HTTP 필터로 보임)
        # Content-Length를 10000으로 크게 선언하여 서버가 요청이 덜 왔다고 판단하게 함
        headers = (
            "POST /api/test/payment/ready HTTP/1.1\r\n"
            f"Host: {HOST}:{PORT}\r\n"
            "Content-Type: application/json\r\n"
            "Content-Length: 10000\r\n"
            "Connection: keep-alive\r\n"
            "User-Agent: RST-Packet-Interceptor/1.0\r\n"
            "\r\n"
        )
        s.sendall(headers.encode())
        print("[slow_sender] HTTP Header 전송 완료 (POST /api/test/payment/ready)")

        # 2. JSON 바디의 핵심 부분만 전송 (서버 결제 로직에 데이터가 적재됨)
        # 서버 수신 버퍼에 이 데이터가 남아있는 상태에서 죽어야 RST가 발생함
        body_head = '{"orderId": "RST-TEST-PAYMENT", "amount": 99000, "userId": 777, "desc": "Capture this packet"'
        s.sendall(body_head.encode())
        print(f"[slow_sender] JSON Body 일부({len(body_head)} bytes) 전송 완료.")

        print("[slow_sender] 서버가 나머지 9,900바이트를 기다리는 중... (이 상태에서 SIGKILL 대기)")

        # 15초 대기 (이 사이에 쉘 스크립트가 SIGKILL 또는 SIGTERM을 쏨)
        time.sleep(15)
        s.close()
        
    except Exception as e:
        print(f"[slow_sender] 오류 발생: {e}")

if __name__ == "__main__":
    send_slow_request()

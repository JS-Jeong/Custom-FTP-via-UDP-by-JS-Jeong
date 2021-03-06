
# UDP-based-FTP

## UDP 기반 FTP

### 무선네트워크 강의 과제 - 2017.5.25
### 팀 구성원
+ 2012161148 조지현 - 서버
+ 2012161154 천지훈 - 서버
+ 2014161124 장연주 - 클라이언트
+ 2014161137 정진서 - 클라이언트

## 목적 및 용도

#### TCP의 슬라이딩 윈도우를 UDP에 구현하여 신뢰성있는 파일전송을 구현한다.
#### 슬라이딩 윈도우의 동작 방식을 알 수 있다.

## 자바 설치
#### 본 애플리케이션은 자바 언어로 작성되었으므로 자바를 먼저 설치해야 한다.

## 구동 절차

1. 서버 구동
	+ 커맨드 입력창을 열고 <pre>.../UDP-based-FTP-Server/bin</pre> 로 이동한다. (cd 명령어)
	+ 로컬에서 테스트하기 위해 <pre>java pack.MainServer 127.0.0.1 11111</pre> 을 입력한다.
	+ 서버 호스트와 포트는 바꿀 수 있다.
	+ 다음과 같은 출력이 나타나면 구동에 성공한 것이다. 이때, 서버는 클라이언트의 접속을 기다리는 상태가 된다.
	<pre>
		s_ip:s_port - /127.0.0.1:11111
		bind success
	</pre>

2. 클라이언트 구동
	+ 마찬가지로 커맨드에서 <pre>.../UDP-based-FTP-Client/bin</pre> 로 이동한다.
	+ 로컬에서 테스트하기 위해 <pre>java pack.MainClient 127.0.0.1 22222 127.0.0.1 11111 ../landscape.jpg 50</pre> 을 입력한다.
	+ 실행 즉시 파일전송이 시작될 것이다. 앞의 두 패러미터는 클라이언트 IP/포트이고 뒤의 두 개는 서버측이다. 그 다음은 전송할 파일의 경로, 전송지연(밀리초)값이다.

3. 전송받은 파일 확인
	+ 디렉토리 <pre>.../UDP-based-FTP-Server</pre> 에서 landscape.jpg 가 잘 저장되었는지 확인한다.

## 동작 설명

1. 클라이언트 측 (코드참조 필수)
	+ 클라이언트는 서버에 접속을 시도하고, 성공시 파일정보를 전송한다.
	<pre>
		c_ip:c_port - /127.0.0.1:22222
		s_ip:s_port - /127.0.0.1:11111
		bind success
		sent file name: ../landscape.jpg, file size: 626967 bytes
	</pre>
	+ 서버측으로부터 파일정보 ACK 메시지를 받는다.
	<pre>
		FILE_INFO_ACK
	</pre>
	+ 이후, 파일 전송이 시작된다.
	+ 윈도우 크기는 10, 각 프래그먼트의 사이즈는 1024바이트, 첫 시퀀스는 0으로 고정되어 있다. (서버측도 동일)
	+ 10개의 시퀀스에 대한 ACK를 서버로부터 모두 받아야 윈도우를 다음 위치로 이동한다. (+10칸) (서버측도 동일)
	+ 슬라이딩 윈도우의 동작을 확인하기 위해 클라이언트 측의 전송 함수는 재정의되어있다 (testSend메소드).
	+ 시퀀스를 전송할 때 일정 확률로 정상적으로 전송되거나, 드롭, 중복(2회)전송된다.
	<pre>
		 1. temp storage created seq: 580 ~ 589
		 2. sent seq 580 --> ok
		 3. sent seq 581 --> droped
		 4. sent seq 582 --> duplicate
		 5. ... 중략
		 6. waiting for acks
		 7. recv ACK 580
		 8. recv ACK 582
		 9. ... 중략
		10. recv ACK 589
		11. resent seq 581 --> ok
		12. recv ACK 581
		13. all acks received
	</pre>
	+ 위는 전송 과정의 한 부분(윈도우)을 보여주는 예시이다.
	+ 1번줄은 혹시 모를 전송 시의 drop을 대비하기 위한 버퍼를 만들어 놓는 것이다.
	+ 2번줄은 시퀀스 580번이 잘 전송됐음을 의미한다.
	+ 3번줄은 시퀀스 581번이 drop됐음을 의미한다.
	+ 4번줄은 시퀀스 582번이 중복됐음을 의미한다.
	+ 6번줄은 앞서 보낸 10개(혹은 미만)의 시퀀스에 대하여 ACK를 기다리겠다는 것이다.
	+ 7번줄과 8번줄을 보면 581 ACK가 오지 않았음을 알 수 있다.
	+ 만약 1초 안에 ACK가 도착하지 않으면 해당 시퀀스만 재전송한다. (11번줄)
	+ 13번줄은 앞서 보낸 시퀀스들에 대한 모든 ACK를 받았음을 의미한다.
	+ 1~13번의 과정이 계속 반복되며, 모든 파일을 읽고 모든 ACK를 다 받으면 다음과 같은 로그를 볼 수 있다.
	<pre>
		(!) all file bytes read
		...
		(!) file transfer finished
	</pre>

2. 서버 측 (코드참조 필수)
	+ 서버는 접속한 클라이언트에 대하여 FILE_INFO를 받고 FILE_INFO_ACK를 보낸다.
	<pre>
		s_ip:s_port - /127.0.0.1:11111
		bind success
		c_ip:c_port - /127.0.0.1:22222
		recv file name: ../landscape.jpg, file size: 626967 bytes
		sent FILE_INFO_ACK
	</pre>
	+ 서버의 가정상황은 클라이언트와 동일하다. (클라이언트 측 참고)
	+ ACK가 drop되거나 중복되는 것은 가정하지 않는다.
	+ 아래는 로그들의 예시를 나타낸 것이다.
	<pre>
		 1. ... (중략)
		 2. recv seq and sent ack 555
		 3. recv seq and sent ack 556
		 4. recv seq and sent ack 557
		 5. dup seq 557 recv --> droped
		 6. recv seq and sent ack 559 [기다림]
		 7. recv seq and sent ack 558
		 8. received bytes: 573440
		 9. ... (중략)
		10. (!) ../landscape.jpg > all data received
	</pre>
	+ 2번줄은 555번 시퀀스를 받았고, ack를 보냈다는 의미이다.
	+ 4~5번줄은 557번 시퀀스를 받았으나 또 받아서(중복) 그냥 버렸다는 의미이다.
	+ 5번과 6번 사이에 558번 시퀀스를 받았어야 하나, 받지 못했다. (클라이언트 측에서 drop됨)
	+ 받지 못한 시퀀스에 대해서 계속 기다린 끝에 7번줄에서 받았다.
	+ 8번줄은 해당 윈도우에 대해서 모든 시퀀스를 받았음을 의미한다. (여태까지 받은 바이트 표시)
	+ 10번줄은 모든 데이터를 받았음을 의미한다.


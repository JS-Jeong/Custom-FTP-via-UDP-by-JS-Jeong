
package pack;

import java.io.*;
import java.net.*;
import java.nio.*;

public class MainClient {


	// 랜덤 전송 ( 정상 OR 드랍 OR 중복 )
	// 결과 리턴 ( "ok" : 정상, "droped" : 드랍, "duplicate" : 중복 )
	private static String testSend( DatagramSocket socket, DatagramPacket packet )
			throws Exception {

		double p = Math.random(); // 0 <= p < 1
		if( p >= 0.05 ) {
			// 정상 95%
			socket.send( packet );
			return "ok";
		}
		else if( p >= 0.025 ) {
			// 드랍 2.5%
			return "droped";
		}
		else {
			// 중복 2.5%
			socket.send( packet );
			socket.send( packet );
			return "duplicate";
		}
	}


	/********************************************************************/


	public static void main( String[] args )
			throws Exception {

		// 서버 IP, 포트
		// 클라이언트 IP, 포트
		// 전송할 파일이름
		// 전송 딜레이
		InetAddress clientIP;
		int clientPort;
		InetAddress serverIP;
		int serverPort;
		String fileName;
		int tmsec;

		if( args.length != 6 ) {
			System.out.println( "usage: java class_name [c_ip] [c_port] [s_ip] [s_port] [f_name] [t_msec]" );

			System.out.println( "default settings..." );

			// 기본 설정값 (로컬)
			clientIP = InetAddress.getByName( "127.0.0.1" );
			clientPort = 22222;
			serverIP = InetAddress.getByName( "127.0.0.1" );
			serverPort = 11111;
			fileName = "../landscape.jpg";
			tmsec = 10;
		}
		else {
			// args 로 변경
			clientIP = InetAddress.getByName( args[ 0 ] );
			clientPort = Integer.parseInt( args[ 1 ] );
			serverIP = InetAddress.getByName( args[ 2 ] );
			serverPort = Integer.parseInt( args[ 3 ] );
			fileName = args[ 4 ];
			tmsec = Integer.parseInt( args[ 5 ] );
		}


		/*=====================================================================*/


		// UDP 소켓 생성
		DatagramSocket socket = new DatagramSocket(
				clientPort, clientIP );

		System.out.println( "c_ip:c_port - " + clientIP + ":" + clientPort );
		System.out.println( "s_ip:s_port - " + serverIP + ":" + serverPort );
		System.out.println( "bind success" );

		// 버퍼
		byte[] buf;
		DatagramPacket pack;

		// 파일 정보 전송 "[파일이름],[파일크기]"
		FileInputStream fin = new FileInputStream( fileName );
		int fileSize = fin.available();
		String msgFileInfo = fileName + "," + fileSize;
		buf = msgFileInfo.getBytes();
		pack = new DatagramPacket(
				buf, buf.length, serverIP, serverPort );
		socket.send( pack );

		System.out.println( "sent file name: " + fileName + ", file size: " + fileSize + " bytes" );

		// 파일 정보 ACK 확인
		buf = new byte[ 1024 ];
		pack = new DatagramPacket( buf, buf.length );
		socket.receive( pack );
		String msgFileInfoACK = new String( pack.getData(), 0, pack.getLength() );

		System.out.println( msgFileInfoACK );
		System.out.println( "=========================" );


		/*=====================================================================*/


		// 파일 전송 시작
		int seq = 0;
		boolean allFileBytesRead = false;

		while( true ) {

			// 윈도우 준비 과정
			int lastIdx = -1;
			boolean[] ackReceiveds = new boolean[ 10 ];
			int[] seqNums = new int[ 10 ];
			DatagramPacket[] packets = new DatagramPacket[ 10 ];
			System.out.println( "temp storage created seq: " + seq + " ~ " + ( seq + 9 ) );

			// 전송 과정
			while( true ) {

				byte[] dataBuf = new byte[ 1024 ];
				int readBytes = fin.read( dataBuf );

				// 파일의 모든 데이터를 읽음
				if( readBytes == -1 ) {
					System.out.println( "waiting for acks" );
					System.out.println( "(!) all file bytes read" );
					allFileBytesRead = true;
					break;
				}

				int length = readBytes;

				// 첫 4 바이트 --> 시퀀스넘버 (SEQ)
				// 다음 4 바이트 --> 길이 (LEN)
				// 다음 LEN 바이트 --> SEQ 의 데이터
				ByteBuffer buffer = ByteBuffer.allocate( 8 + length );
				buffer.putInt( seq );
				buffer.putInt( length );
				buffer.put( dataBuf, 0, length );

				// 시퀀스 전송
				byte[] bufferArr = buffer.array();
				DatagramPacket packet = new DatagramPacket(
						bufferArr, bufferArr.length, serverIP, serverPort );
				String res = testSend( socket, packet );

				// 임시 저장
				// --> ACK 를 못받은 패킷(SEQ)에 대하여 대처 가능
				lastIdx ++;
				ackReceiveds[ lastIdx ] = false;
				seqNums[ lastIdx ] = seq;
				packets[ lastIdx ] = packet;

				System.out.println( "sent seq " + seq ++ + " --> " + res );

				// 딜레이
				try { Thread.sleep( tmsec ); }
				catch( Exception ex ) {}

				if( lastIdx == 9 ) {
					System.out.println( "waiting for acks" );
					break;
				}
			}

			// ACK 확인 과정
			while( true ) {
				byte[] ackBuf = new byte[ 1024 ];
				DatagramPacket packet = new DatagramPacket( ackBuf, ackBuf.length );
				socket.setSoTimeout( 1000 ); // 1 초 타임아웃

				try {
					socket.receive( packet ); // ACK (파싱전) 받기
				}
				catch( SocketTimeoutException ex ) {
					// ACK 를 아직 다 받지 못한 상태에서의 타임아웃

					// ACK 를 모두 받았는지 다시 확인
					boolean allReceived = true;
					for( int i = 0; i <= lastIdx; i ++ ) {
						if( !ackReceiveds[ i ] ) {
							allReceived = false;
							break;
						}
					}
					if( !allReceived ) {
						// 모든 ACK 을 받지 못함
						// ACK 을 받지 못한 SEQ 에 대하여 재전송
						for( int i = 0; i <= lastIdx; i ++ ) {
							if( !ackReceiveds[ i ] ) {
								String res = testSend( socket, packets[ i ] );
								System.out.println( "resent seq " + seqNums[ i ] + " --> " + res );

								// 딜레이
								try { Thread.sleep( tmsec ); }
								catch( Exception ex2 ) {}
							}
						}
						continue;
					}
					else {
						break;
					}
				}

				// ACK 확인 및 반영
				String ackStr = new String( packet.getData(), 0, packet.getLength() );
				int ackNum = Integer.parseInt( ackStr );
				for( int i = 0; i <= lastIdx; i ++ ) {
					if( seqNums[ i ] == ackNum ) {
						ackReceiveds[ i ] = true;
						break;
					}
				}

				System.out.println( "recv ACK " + ackNum );

				// ACK 를 모두 받았는지 확인
				boolean allReceived = true;
				for( int i = 0; i <= lastIdx; i ++ ) {
					if( !ackReceiveds[ i ] ) {
						allReceived = false;
						break;
					}
				}
				if( !allReceived ) {
					// 모든 ACK 을 받지 못함
					continue;
				}
				else {
					System.out.println( "all acks received" );
					break;
				}
			}

			// 모든 파일을 읽음 --> 전송 종료
			if( allFileBytesRead ) {
				System.out.println( "(!) file transfer finished" );
				break;
			}
		}

		socket.close();
		fin.close();
	}
}

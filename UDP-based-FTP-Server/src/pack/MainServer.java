
package pack;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;

public class MainServer {

	public static void main( String[] args ) throws Exception {

		// 서버 IP, 포트
		InetAddress serverIP;
		int serverPort;

		if( args.length != 2 ) {
			System.out.println( "usage: java class_name [s_ip] [s_port]" );

			System.out.println( "default settings..." );

			// 기본 설정값 (개발환경)
			serverIP = InetAddress.getByName( "127.0.0.1" );
			serverPort = 11111;
		}
		else {
			// args 로 변경
			serverIP = InetAddress.getByName( args[ 0 ] );
			serverPort = Integer.parseInt( args[ 1 ] );
		}


		/*=====================================================================*/



		// 클라이언트에 대한 루프
		while( true ) {

			// UDP 소켓 생성
			DatagramSocket socket = new DatagramSocket(
					serverPort, serverIP );

			System.out.println( "s_ip:s_port - " + serverIP + ":" + serverPort );
			System.out.println( "bind success" );

			// 클라이언트 변수
			InetAddress clientIP;
			int clientPort;

			byte[] buf;
			DatagramPacket packet;

			// 파일정보 받기
			buf = new byte[ 1024 ];
			packet = new DatagramPacket( buf, buf.length );
			socket.receive( packet );

			clientIP = packet.getAddress();
			clientPort = packet.getPort();

			System.out.println( "c_ip:c_port - " + clientIP + ":" + clientPort );

			String msgFileInfo = new String( packet.getData(), 0, packet.getLength() );
			String[] parts = msgFileInfo.split( "," );
			String fileName = parts[ 0 ];
			int fileSize = Integer.parseInt( parts[ 1 ] );

			System.out.println( "recv file name: " + fileName + ", file size: " + fileSize + " bytes" );

			// 파일정보 ACK 전달
			String msgFileInfoACK = "FILE_INFO_ACK";
			buf = msgFileInfoACK.getBytes();
			packet = new DatagramPacket( buf, buf.length, clientIP, clientPort );
			socket.send( packet );

			System.out.println( "sent " + msgFileInfoACK );
			System.out.println( "====================================" );

			// 수신 준비
			int receivedBytes = 0;
			int seqFirst = 0;
			FileOutputStream fout = new FileOutputStream( fileName );

			// 해당 클라이언트의 파일전송에 대한 루프
			while( true ) {

				// 앞으로 받아야 하는 바이트를 예측 ( 최대 1024 x 10 )
				int lastIdx = -1;
				int bytesToReceive = fileSize - receivedBytes;
				if( bytesToReceive >= 1024 * 10 ) {
					bytesToReceive = 1024 * 10;
					lastIdx = 9;
				}
				else {
					int a = bytesToReceive / 1024;
					int r = ( bytesToReceive % 1024 ) > 0 ? 1 : 0;
					lastIdx += a + r;
				}

				boolean[] seqReceiveds = new boolean[ 10 ];
				byte[][] dataArr = new byte[ 10 ][];


				// 현재 윈도우에 대한 수신 루프
				while( true ) {

					buf = new byte[ 4 + 4 + 1024 ];
					packet = new DatagramPacket( buf, buf.length );
					socket.receive( packet );
					ByteBuffer buffer = ByteBuffer.allocate( packet.getLength() );
					buffer.put( packet.getData(), 0, packet.getLength() );
					buffer.position( 0 );

					// 분기용 루프
					while( true ) {

						int seq = buffer.getInt();

						// 중복 패킷의 경우 1
						if( seq < seqFirst ) {
							System.out.println( "dup seq " + seq + " recv --> droped" );
							break;
						}

						int idx = seq - seqFirst;
						int len = buffer.getInt();
						byte[] data = new byte[ len ];
						buffer.get( data, 0, len );
						seqReceiveds[ idx ] = true;

						// 중복 패킷의 경우 2
						if( dataArr[ idx ] != null ) {
							System.out.println( "dup seq " + seq + " recv --> droped" );
							break;
						}

						dataArr[ idx ] = data;

						// ACK 전송
						byte[] ackBytes =  ( "" + seq ).getBytes();
						packet = new DatagramPacket( ackBytes, ackBytes.length, clientIP, clientPort );
						socket.send( packet );

						System.out.println( "recv seq and sent ack " + seq );
						break;
					}


					boolean allSeqReceived = true;
					for( int i = 0; i <= lastIdx; i ++ ) {
						if( !seqReceiveds[ i ] ) {
							allSeqReceived = false;
							break;
						}
					}
					if( !allSeqReceived ) {
						continue;
					}
					else {
						break;
					}
				}

				for( int i = 0; i <= lastIdx; i ++ ) {
					receivedBytes += dataArr[ i ].length;
					fout.write( dataArr[ i ] );
					fout.flush();
				}
				System.out.println( "received bytes: " + receivedBytes );

				seqFirst += 10;
				if( receivedBytes == fileSize ) {
					System.out.println( "(!) " + fileName + " > all data received" );
					break;
				}
			}
			fout.close();
			socket.close();
			System.out.println( "=======================================" );
		}
	}
}

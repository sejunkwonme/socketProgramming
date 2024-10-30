import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class IMAPEmailInfoFetcher {
    String email, password, emailID, server, boxType;
    int port;

    public IMAPEmailInfoFetcher(
            String email, String password, String emailID,
            String server, int port, String boxType
    ) {
        this.email = email;
        this.password= password;
        this.emailID = emailID;
        this.server = server;
        this.port = port;
        this.boxType = boxType;
    }

    /*
        특정 메일의 발신자 이메일, 수신 날짜, 메일 제목, 메일 내용 가져오기
     */
    public void fetchEmailInfo() {
        try {
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            try (SSLSocket socket = (SSLSocket) factory.createSocket(server, port);
                 PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                 /*
                    서버 초기 응답
                  */
                 System.out.println("S: " + reader.readLine());

                 /*
                    로그인 명령어 전송 및 응답 확인
                  */
                 writer.println("a1 LOGIN " + email + " " + password);
                 System.out.println("C: a1 LOGIN " + email + " " + password);

                 System.out.println("S: " + reader.readLine());

                /*
                    메일함 선택
                    EX) INBOX는 수신메일함, SENT는 발신메일함
                 */
                String line;
                writer.println("a2 SELECT " + boxType);
                System.out.println("C: a2 SELECT " + boxType);
                while((line = reader.readLine()) != null) {
                    System.out.println("S: " + line);
                    if (line.contains("a2 OK")) {
                        break;
                    }
                }

                /*
                    특정 이메일(emailID)의 헤더에서 발신자 이메일, 수신 날짜, 메일 제목 정보를 가져오기
                 */
                writer.println("a3 FETCH " + emailID + " (BODY[HEADER.FIELDS (FROM DATE SUBJECT)])");
                System.out.println("C: a3 FETCH " + emailID + " (BODY[HEADER.FIELDS (FROM DATE SUBJECT)])");
                StringBuilder headerInfo = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    System.out.println("S: " + line);
                    headerInfo.append(line).append("\n");
                    if (line.contains("a3 OK")) {
                        break;
                    }
                }

                System.out.println("메일 헤더 정보(디코딩 전):\n" + headerInfo);

                /*
                    디코딩 후 출력

                    ** 인코딩 가능 형식 **
                    1. Base64 인코딩
                    2. Quoted-Printable 인코딩
                    3. 일반 텍스트
                 */
                String decodedHeader = decodeMimeEncodedText(headerInfo.toString());
                System.out.println("메일 헤더 정보(디코딩 후):\n" + decodedHeader);

                /*
                    이메일 본문 가져오기
                 */
//                writer.println("a4 FETCH " + emailID + " BODY[TEXT]");
//                System.out.println("C: a4 FETCH " + emailID + " BODY[TEXT]");
//
////                StringBuilder bodyContent = new StringBuilder();
////                while ((line = reader.readLine()) != null) {
////                    bodyContent.append(line).append("\n");
////                    if (line.contains("a4 OK")) {
////                        break;
////                    }
////                }
////                System.out.println("메일 본문 정보:\n" + bodyContent);
////                // Quoted-Printable 디코딩 및 출력
////                String decodedBodyContent = decodeQuotedPrintable(bodyContent.toString());
////                System.out.println("디코딩된 메일 본문 정보:\n" + decodedBodyContent);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private String decodeMimeEncodedText(String text) {
        StringBuilder decodedText = new StringBuilder();

        /*
            Base64 또는 Quoted-Printable로 인코딩된 텍스트를 찾기 위해 패턴 사용
            =?charset?encodingType?encodedText?= 형식
         */
        Pattern pattern = Pattern.compile("=\\?(.*?)\\?([BQ])\\?(.+?)\\?=", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        int lastEnd = 0;

        while (matcher.find()) {
            decodedText.append(text, lastEnd, matcher.start());

            /*
                매칭된 그룹에서 charset, 인코딩 타입, 인코딩된 텍스트 추출
             */
            String charsetName = matcher.group(1);
            String encodingType = matcher.group(2);
            String encodedText = matcher.group(3);

            Charset charset = Charset.forName(charsetName);
            byte[] decodedBytes;

            try {
                // 인코딩 타입이 'B"인 경우 Base64로 디코딩
                if (encodingType.equalsIgnoreCase("B")) {
                    decodedBytes = Base64.getDecoder().decode(encodedText);
                } else {
                    // "Q"인 경우 Quoted-Printable로 디코딩
                    // decodeQuotedPrintable 메소드에서 charset을 사용하여 디코딩
                    decodedBytes = decodeQuotedPrintable(encodedText).getBytes(charset);
                }
                // 디코딩된 바이트 배열을 charset으로 변환하여 문자열을 추가
                decodedText.append(new String(decodedBytes, charset));
            } catch (Exception e) {
                // 디코딩 실패 시 원본 텍스트 그대로 추가
                decodedText.append(matcher.group(0));
            }
            // 마지막 매칭 위치를 현재 매칭 끝 위치로 업데이트
            lastEnd = matcher.end();
        }
        // 마지막 남은 텍스트 추가
        decodedText.append(text.substring(lastEnd));
        return decodedText.toString();
    }
    
    /*
        Quoted-Printable 디코딩
     */
    private String decodeQuotedPrintable(String text) {
        // 줄바꿈과 이어진 `=` 기호 제거
        text = text.replaceAll("=\r?\n", "");

        StringBuilder decoded = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '=' && i + 2 < text.length()) {
                String hex = text.substring(i + 1, i + 3);
                try {
                    int value = Integer.parseInt(hex, 16);
                    decoded.append((char) value);
                    i += 2;
                } catch (NumberFormatException e) {
                    decoded.append(c); // 실패 시 '=' 그대로 추가
                }
            } else {
                decoded.append(c);
            }
        }
        return decoded.toString();
    }

    private String extractContentTransferEncoding(String header) {
        Pattern pattern = Pattern.compile("Content-Transfer-Encoding:\\s*(\\S+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(header);
        return matcher.find() ? matcher.group(1).toLowerCase() : "7bit"; // 기본 7bit로 설정
    }

    private String decodeContent(String content, String encoding) {
        switch (encoding) {
            case "base64":
                return new String(Base64.getDecoder().decode(content), StandardCharsets.UTF_8);
            case "quoted-printable":
                return decodeQuotedPrintable(content);
            default:
                return content;
        }
    }
    
    public static void main(String[] args) {
        // 네이버 IMAP 서버 테스트
        String naverEmail = "audtn0099@naver.com";
        String naverPassword = "msjw0706";
        String emailID = "1";
        String server = "imap.naver.com";
        int port = 993;
        String boxType = "INBOX";

        for(int i = 1; i <= 5; i++) {
            IMAPEmailInfoFetcher naverEmailInfoFetcher = new IMAPEmailInfoFetcher(
                    naverEmail, naverPassword, Integer.toString(i),
                    server, port, boxType
            );
            naverEmailInfoFetcher.fetchEmailInfo();
        }
    }
}

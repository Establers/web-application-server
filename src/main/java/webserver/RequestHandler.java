package webserver;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import db.DataBase;
import model.User;
import util.HttpRequestUtils;
import util.IOUtils;


public class RequestHandler extends Thread {
    private static final Logger log = LoggerFactory.getLogger(RequestHandler.class);

    private Socket connection;

    public RequestHandler(Socket connectionSocket) {
        this.connection = connectionSocket;
    }

    public void run() {
        log.debug("New Client Connect! Connected IP : {}, Port : {}", connection.getInetAddress(),
                connection.getPort());

        try (InputStream in = connection.getInputStream(); OutputStream out = connection.getOutputStream()) {
            // TODO 사용자 요청에 대한 처리는 이 곳에 구현하면 된다.
        	
        	BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        	// decoding 방식 지정
        	
        	String lineString = br.readLine();
        	log.debug("HTTP Header : {}", lineString); // {} 에 lineString (마치 파이썬의 formating print)
        	
        	if (lineString == null) {
        		return;
        	}
        	
        	String tokens[] = lineString.split(" ");
        	// GET /index.html HTTP/1.1
        	
        	// 2. 전부 출력
        	// br.readline으로 계속 읽어오고 끝을 지정해주면 된다. 
        	int contentLength = 0;
        	boolean isLogined = false;
        	
        	while (true) {
        		lineString = br.readLine();
        		if (lineString == null || lineString.equals("")) {
        			break;
        		}
        		log.debug("HTTP Request Header : {}", lineString);
        		if(lineString.contains("Content-Length")) {
        			String temps[] = lineString.split(": "); 
        			contentLength = Integer.parseInt(temps[1]);
        		}
        		
        		if(lineString.contains("Cookie")) {
        			String temps[] = lineString.split(":");
        			Map<String, String> cookies = HttpRequestUtils.parseCookies(temps[1].trim());
        			String valueCookie = cookies.get("logined");
        			log.debug("valueCookie: {}", valueCookie);
        			if(valueCookie == null) {
        				// 로그인 O
        				isLogined = false;
        			} else {
        				// 로그인 X
        				isLogined = Boolean.parseBoolean(valueCookie);
        			}
        			log.debug("isLogined : {}", isLogined);
        		}
        	}
        	
        	
            
            // 요구사항 2
            // 1. /user/create? 로 시작하는 지 확인
            if (tokens[1].startsWith("/user/create?")) {
            	Map<String, String> queryParams = HttpRequestUtils.parseQueryString(tokens[1].substring("/user/create?".length()));
            	// /user/create? 를 제거한 뒤, 파싱한다.
            	
            	// User 클래스 객체를 생성한다.
                User user = new User(
                	queryParams.get("userId"),
                	queryParams.get("password"),
                	queryParams.get("name"),
                	queryParams.get("email")
                ); 
                // 생성 확인
                log.debug("user : {}", user.toString());
            } else if (tokens[1].equals("/user/create")) { // POST
                String bodyData = IOUtils.readData(br, contentLength);
                log.debug("HTTP Request bodyData : {}", bodyData);
                // userId=123&password=12332&name=123&email=123%4022
                // 형식이 요구사항 2와 똑같다.
                Map<String, String> queryParams = HttpRequestUtils.parseQueryString(bodyData);
                User user = new User(
                	queryParams.get("userId"),
                	queryParams.get("password"),
                	queryParams.get("name"),
                	queryParams.get("email")
            	);
                DataBase.addUser(user); // 요구사항 5
                
                log.debug("user : {}", user.toString());
                // user : User [userId=123, password=12332, name=123, email=123%4022]
                
                // 회원가입 시 index.html 로 이동
                DataOutputStream dos = new DataOutputStream(out);
                response302Header(dos, "/index.html");
                
            } else if (tokens[1].equals("/user/login")) { // HTTP Header : POST /user/login HTTP/1.1
                String bodyData = IOUtils.readData(br, contentLength);
                log.debug("HTTP Request bodyData : {}", bodyData);
                Map<String, String> queryParams = HttpRequestUtils.parseQueryString(bodyData);
                
                // 1. 로그인하려는 아이디가 있는지 확인
                User user = DataBase.findUserById(queryParams.get("userId"));
                if (user == null) {
                	// 존재하지 않음 -> 로그인 실패 -> loginFailed 이동 - 아무것도 없이 이동 -> index로
                    DataOutputStream dos = new DataOutputStream(out);
                    byte[] body = Files.readAllBytes(new File("./webapp" + "/user/login_failed.html").toPath());
                    
                    response200HeaderLoginFailed(dos, body.length);
                    responseBody(dos, body); 	
                }
                
                // 아이디 존재
                if (user.getPassword().equals(queryParams.get("password"))) {
                	// 비밀번호 같음 -> 로그인 성공
                	// HTTP Header 에 값을 Set-Cookie 를 달아서 보내는 거
                	log.debug("Login Success !");
                	DataOutputStream dos = new DataOutputStream(out);
                	response302HeaderLoginSuccess(dos);
                	
                } else {
                	// 비밀번호 다름 ->  로그인 실패 -> loginFailed 이동 - 아무것도 없이 이동 -> index로
                    DataOutputStream dos = new DataOutputStream(out);
                    byte[] body = Files.readAllBytes(new File("./webapp" + "/user/login_failed.html").toPath());
                    
                    response200HeaderLoginFailed(dos, body.length);
                    responseBody(dos, body); 
                	
                }
                
                
            } else if(tokens[1].equals("/user/list")) { // 요구사항 5
            	log.debug("USER LIST ! ");
            	// Cookie 값 확인 -> 헤더에서 해야함
            	if(!isLogined) {
                    DataOutputStream dos = new DataOutputStream(out);
                    byte[] body = Files.readAllBytes(new File("./webapp" + "/user/login.html").toPath());
                    
                    response200HeaderLoginFailed(dos, body.length);
                    responseBody(dos, body); 
            		return;
            	} 
            	
            	log.debug("User Logined ! ");
            	// 로그인 O
            	Collection<User> userList = DataBase.findAll();
            	
            	// HTML 동적으로 넣어라고? 미친
            	StringBuilder sb = new StringBuilder();

            	sb.append("<table border='1'>");
            	for(User u : userList) {
            		sb.append("<tr>");
            		sb.append("<td>" + u.getUserId() + "</td>");
            		sb.append("<td>" + u.getName() + "</td>");
            		sb.append("<td>" + u.getEmail() + "</td>");
            		sb.append("</tr>");
            	}
            	sb.append("</table>");
            	
            	DataOutputStream dos = new DataOutputStream(out);
            	byte[] body = sb.toString().getBytes();
            	
            	response200Header(dos, body.length);
                responseBody(dos, body); 
            	
            } else if(tokens[1].endsWith(".css")) {
            	// Content-Type 을 보낸다.
            	DataOutputStream dos = new DataOutputStream(out);
            	
            	byte[] body = Files.readAllBytes(new File("./webapp" + tokens[1]).toPath());
            	
            	response200HeaderCss(dos, body.length);
            	responseBody(dos, body); 
            	
            } else {
            	// 1-3. 요청 URL : /index.html
            	// 해당하는 파일을 읽어 body에 실기 
//            	String bodyData = IOUtils.readData(br, 77);
//                log.debug("bodyData {}", bodyData);
            	
                DataOutputStream dos = new DataOutputStream(out);
                // byte[] body = "Hello World".getBytes();
                byte[] body = Files.readAllBytes(new File("./webapp" + tokens[1]).toPath());
                // File 클래스의 File 객체를 Path객체로 변환시키는 toPath() 메서드 사용
                // readAllBytes는 Parameter 타입을 Path로 받는다.
                
                // 그럼 왜 굳이 new File 로 하는 것인가? -> 파일 시스템에서 파일의 위치를 가진
                // Path 객체를 얻어내기 위해 사용한다.
                // System.out.println(new File("./webapp" + tokens[1]).toString());
                // .\webapp\index.html
                
                response200Header(dos, body.length);
                responseBody(dos, body); 
                
                // 요구사항 3 
                // 3-1 body에 값이 어떻게 넘어오는지 확인

            }
            
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void response200Header(DataOutputStream dos, int lengthOfBodyContent) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: text/html;charset=utf-8\r\n");
            dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }
    
    private void response200HeaderCss(DataOutputStream dos, int lengthOfBodyContent) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: text/css\r\n");
            dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }
    
    private void response200HeaderLoginFailed(DataOutputStream dos, int lengthOfBodyContent) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: text/html;charset=utf-8\r\n");
            dos.writeBytes("Set-Cookie: logined=false\r\n");
            dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }
    
    private void response302HeaderLoginSuccess(DataOutputStream dos) {
        try {
            dos.writeBytes("HTTP/1.1 302 Found \r\n");
            dos.writeBytes("Set-Cookie: logined=true\r\n");
            dos.writeBytes("Location: /index.html \r\n");
       
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }
    
    private void responseBody(DataOutputStream dos, byte[] body) {
        try {
            dos.write(body, 0, body.length);
            dos.flush();
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }
    
    // 요구사항 4
    private void response302Header(DataOutputStream dos, String url) {
        try {
            dos.writeBytes("HTTP/1.1 302 Found \r\n"); // \r\n : http 표준 줄바꿈 :CRLF
            dos.writeBytes("Location: " + url + "\r\n");

        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }
}

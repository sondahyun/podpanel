# PodPanel

Android에서 호환 무선 이어폰의 배터리 상태를 표시하고, 지원되는 경우 노이즈 컨트롤을 전환하는 앱입니다.

## 구성

| 모듈 | 역할 |
|---|---|
| `:protocol` | 광고 패킷 디코더, 명령 코덱, 연결 상태 머신 |
| `:design` | 공통 UI 구성 요소와 그래픽 |
| `:app` | Android 화면, 블루투스 연결, 위젯, 퀵 설정 타일 |

## 빌드와 검사

```bash
./gradlew assembleDebug test lint
```

연결과 제어 기능은 기기, OS, 권한, 이어폰 펌웨어에 따라 다를 수 있습니다. 앱의 연결 진단 화면에서 현재 기기에서의 연결 상태를 확인할 수 있습니다.

## 라이선스

포함된 서드파티 구성 요소와 고지는 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)를 참고하세요.

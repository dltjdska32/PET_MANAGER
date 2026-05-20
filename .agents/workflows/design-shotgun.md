---
name: design-shotgun
preamble-tier: 2
version: 1.0.0
description: |
  Design shotgun: generate multiple AI design variants, open a comparison board,
  collect structured feedback, and iterate. Standalone design exploration you can
  run anytime. Use when: "explore designs", "show me options", "design variants",
  "visual brainstorm", or "I don't like how this looks".
  Proactively suggest when the user describes a UI feature but hasn't seen
  what it could look like. (gstack)
allowed-tools:
  - Bash
  - Read
  - Glob
  - Grep
  - Agent
  - AskUserQuestion
---

# /design-shotgun: Visual Design Exploration

You are a design brainstorming partner. Generate multiple AI design variants, open them
side-by-side in the user's browser, and iterate until they approve a direction. This is
visual brainstorming, not a review process.

## Step 0: Session Detection
(안드로이드 환경에 맞춰 내부적으로 세션 관리 진행)

## Step 1: Context Gathering
필요한 컨텍스트:
1. **Who** — 반려동물 보호자 (Pet Owners)
2. **Job to be done** — 쉽고 빠른 로그인 및 회원가입 (반려동물 정보 등록 전 단계)
3. **What exists** — Jetpack Compose 기반의 최신 안드로이드 UI 구성 요소
4. **User flow** — 앱 초기 진입 -> 로그인/소셜 로그인 -> (미가입 시) 회원가입 -> 메인 피드
5. **Edge cases** — 입력 오류, 중복 아이디, 네트워크 지연, 다크 모드 대응

## Step 2: Taste Memory
사용자는 현대적이고 세련된, 그러면서도 반려동물 앱 특유의 따뜻함이 느껴지는 디자인을 선호함.

## Step 3: Generate Variants
3가지 컨셉으로 시안을 생성합니다.

## Step 4: Comparison Board + Feedback Loop
생성된 이미지를 제시하고 피드백을 받습니다.

## Step 5: Feedback Confirmation
피드백을 요약하고 확정합니다.

## Step 6: Save & Next Steps
최종안을 바탕으로 실제 구현 코드로 전환합니다.

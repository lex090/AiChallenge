# Branching Context Management — Design Spec

## Overview

Новая стратегия управления контекстом — **Branching** (ветки диалога). Позволяет создавать checkpoint в диалоге, ответвлять новые ветки от любого Turn, продолжать диалог в каждой ветке независимо и переключаться между ветками.

Branching — третья стратегия `ContextManagementType`, наравне с `None` и `SummarizeOnThreshold`.

## Requirements

### Ветвление
- Ветвление от любого Turn в любой ветке (произвольная глубина дерева)
- При ветвлении от Turn N — turns после N остаются в оригинальной ветке, новая ветка начинается пустой
- Неявная "main" ветка создаётся автоматически при выборе стратегии Branching
- Main ветка неудаляема, остальные можно удалить

### Контекст для LLM
- Линейный путь от корня сессии до кончика текущей ветки
- Пример: main [T1,T2,T3] → branch-A [T6,T7] → branch-B [T9,T10] → контекст для branch-B: [T1,T2,T3,T6,T7,T9,T10]

### Удаление
- Каскадное удаление: удаление ветки удаляет все дочерние ветки и их turns
- Confirmation dialog обязателен

---

## Domain Models (modules/core)

### Новые типы

```kotlin
@JvmInline
value class BranchId(val value: String) {
    companion object {
        fun generate(): BranchId = BranchId(UUID.randomUUID().toString())
    }
}

data class Branch(
    val id: BranchId,
    val sessionId: AgentSessionId,
    val name: String,
    val parentTurnId: TurnId?,   // null для main ветки
    val isMain: Boolean,
    val createdAt: Instant,
)
```

### ContextManagementType — новый вариант

```kotlin
sealed interface ContextManagementType {
    data object None : ContextManagementType
    data object SummarizeOnThreshold : ContextManagementType
    data object Branching : ContextManagementType
}
```

### Turn — без изменений

Turn остаётся как есть. Связь Turn ↔ Branch через mapping table (BranchTurnRepository).

---

## Repository Interfaces (modules/core)

### BranchRepository

```kotlin
interface BranchRepository {
    suspend fun create(branch: Branch): BranchId
    suspend fun get(branchId: BranchId): Branch?
    suspend fun getBySession(sessionId: AgentSessionId): List<Branch>
    suspend fun getMainBranch(sessionId: AgentSessionId): Branch?
    suspend fun delete(branchId: BranchId)
}
```

### BranchTurnRepository

```kotlin
interface BranchTurnRepository {
    suspend fun append(branchId: BranchId, turnId: TurnId, orderIndex: Int)
    suspend fun getTurnIds(branchId: BranchId): List<TurnId>
    suspend fun findBranchByTurnId(turnId: TurnId): BranchId?
    suspend fun getMaxOrderIndex(branchId: BranchId): Int?
    suspend fun deleteByBranch(branchId: BranchId)
}
```

### ActiveBranchRepository

```kotlin
interface ActiveBranchRepository {
    suspend fun set(sessionId: AgentSessionId, branchId: BranchId)
    suspend fun get(sessionId: AgentSessionId): BranchId?
    suspend fun delete(sessionId: AgentSessionId)
}
```

---

## Database Schema (Exposed tables)

### BranchesTable

```kotlin
object BranchesTable : Table("branches") {
    val id = varchar("id", 36)
    val sessionId = varchar("session_id", 36)
    val name = varchar("name", 255)
    val parentTurnId = varchar("parent_turn_id", 36).nullable()
    val isMain = bool("is_main")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}
```

### BranchTurnsTable

```kotlin
object BranchTurnsTable : Table("branch_turns") {
    val branchId = varchar("branch_id", 36)
    val turnId = varchar("turn_id", 36)
    val orderIndex = integer("order_index")
    override val primaryKey = PrimaryKey(branchId, turnId)
}
```

### ActiveBranchTable

```kotlin
object ActiveBranchTable : Table("active_branch") {
    val sessionId = varchar("session_id", 36)
    val branchId = varchar("branch_id", 36)
    override val primaryKey = PrimaryKey(sessionId)
}
```

---

## Context Management Logic

### Интеграция в DefaultContextManager

`DefaultContextManager` делегирует в `BranchingContextManager` когда стратегия = `Branching`. `BranchingContextManager` реализует интерфейс `ContextManager`.

Сигнатура `prepareContext(sessionId, newMessage)` не меняется. `BranchingContextManager` сам определяет активную ветку через `ActiveBranchRepository`.

### BranchingContextManager

Размещается в `modules/domain/context-manager`.

```kotlin
class BranchingContextManager(
    private val turnRepository: TurnRepository,
    private val branchRepository: BranchRepository,
    private val branchTurnRepository: BranchTurnRepository,
    private val activeBranchRepository: ActiveBranchRepository,
) : ContextManager {

    override suspend fun prepareContext(
        sessionId: AgentSessionId,
        newMessage: String,
    ): PreparedContext {
        val branchId = activeBranchRepository.get(sessionId)
            ?: error("No active branch for session")
        val turns = collectBranchPath(branchId)
        // Формируем messages из turns + newMessage → PreparedContext
    }

    private suspend fun collectBranchPath(branchId: BranchId): List<Turn> {
        // 1. Получить текущую ветку
        // 2. Получить turnIds ветки через branchTurnRepository
        // 3. Если ветка имеет parentTurnId — найти ветку-владельца этого turn
        // 4. Получить turns родительской ветки до parentTurnId (включительно)
        // 5. Рекурсия до main (parentTurnId = null)
        // 6. Склеить всё в линейный список
    }
}
```

### Алгоритм collectBranchPath

```
collectBranchPath(branchId):
  branch = branchRepository.get(branchId)
  myTurnIds = branchTurnRepository.getTurnIds(branchId)
  myTurns = myTurnIds.map { turnRepository.get(it) }

  if branch.parentTurnId == null:
    return myTurns  // это main, корень

  // Найти ветку-владельца parentTurn
  parentBranchId = branchTurnRepository.findBranchByTurnId(branch.parentTurnId)
  parentPath = collectBranchPath(parentBranchId)

  // Обрезать parentPath до parentTurnId включительно
  cutIndex = parentPath.indexOfFirst { it.id == branch.parentTurnId }
  trunk = parentPath.subList(0, cutIndex + 1)

  return trunk + myTurns
```

### Операция "отправить сообщение" (send flow)

```
AiAgent.send(sessionId, message):
  1. contextManager.prepareContext(sessionId, message) → PreparedContext
     (BranchingContextManager читает activeBranch, собирает path)
  2. LLM call с PreparedContext.messages
  3. Создать Turn → turnRepository.append(sessionId, turn)
  4. Получить activeBranchId из activeBranchRepository
  5. branchTurnRepository.append(activeBranchId, turnId, nextOrderIndex)
  6. Записать token/cost details
```

### Операция "создать ветку"

```
createBranch(sessionId, name, parentTurnId):
  1. Проверить стратегия = Branching
  2. Branch(id=generate(), sessionId, name, parentTurnId, isMain=false, createdAt=now())
  3. branchRepository.create(branch)
  4. return branchId
```

### Операция "удалить ветку" (каскадно)

```
deleteBranch(branchId):
  1. branch = branchRepository.get(branchId)
  2. Проверить branch.isMain == false
  3. Найти все дочерние ветки (рекурсивно):
     - получить turnIds этой ветки
     - найти все ветки чей parentTurnId ∈ turnIds
     - рекурсия для каждой дочерней
  4. Для каждой ветки (от листьев к корню):
     a. branchTurnRepository.deleteByBranch(branchId)
     b. Удалить turns, принадлежащие только этой ветке
     c. branchRepository.delete(branchId)
  5. Если удалённая ветка была активной — переключить на main
```

### Операция "переключить ветку"

```
switchBranch(sessionId, branchId):
  1. Проверить ветка принадлежит сессии
  2. activeBranchRepository.set(sessionId, branchId)
```

### Переключение стратегии на Branching

Когда пользователь выбирает стратегию Branching через `updateContextManagementType`:

```
updateContextManagementType(sessionId, Branching):
  1. contextManagementRepository.save(sessionId, Branching)
  2. Проверить: существует ли main ветка для сессии?
  3. Если нет — создать main Branch(parentTurnId=null, isMain=true, name="main")
  4. Существующие turns сессии → привязать к main через BranchTurnRepository
  5. activeBranchRepository.set(sessionId, mainBranchId)
```

При переключении с Branching на другую стратегию — ветки и маппинги остаются в БД (не удаляются). Если пользователь вернётся на Branching — всё восстановится.

---

## Agent Interface Changes

Новые методы в `Agent`:

```kotlin
interface Agent {
    // ... существующие методы ...

    suspend fun createBranch(
        sessionId: AgentSessionId,
        name: String,
        parentTurnId: TurnId,
    ): Either<AgentError, BranchId>

    suspend fun deleteBranch(
        branchId: BranchId,
    ): Either<AgentError, Unit>

    suspend fun getBranches(
        sessionId: AgentSessionId,
    ): Either<AgentError, List<Branch>>

    suspend fun switchBranch(
        sessionId: AgentSessionId,
        branchId: BranchId,
    ): Either<AgentError, Unit>

    suspend fun getActiveBranch(
        sessionId: AgentSessionId,
    ): Either<AgentError, Branch?>
}
```

---

## UI/UX Design

### Доступность

Весь UI веток появляется **только когда стратегия контекста = Branching**. При других стратегиях чат работает как сейчас.

### Создание ветки

- Кнопка "⑂" появляется при ховере на сообщение (action button)
- По нажатию — popup: поле ввода имени ветки + кнопка "Создать"
- Имя по умолчанию — "Branch N"

### Удаление ветки

- Confirmation dialog: "Удалить ветку 'X' и все дочерние ветки? Это действие необратимо."
- Кнопки: Отмена / Удалить

### Переключение веток

- При переключении чат перезагружает историю: общий ствол до точки ветвления + turns выбранной ветки
- Маркеры точек ветвления видимы в чате (показывают какие ветки отходят от этого Turn)

### Маркер точки ветвления в чате

В потоке сообщений, после Turn от которого отходят ветки, отображается компактный маркер:
```
⑂ Точка ветвления — [→ REST подход] [→ GraphQL подход]
```
Теги-ветки кликабельны для быстрого переключения.

### Три варианта навигации (будут реализованы в отдельных worktree для сравнения)

#### Вариант A: Tab Bar над чатом
- Горизонтальные табы с именами веток над областью сообщений
- Активный таб подсвечен
- Main без кнопки закрытия, остальные — с крестиком
- Кнопка "+" для создания ветки от последнего turn
- Плюсы: привычный паттерн, мгновенное переключение, все ветки видны
- Минусы: не показывает иерархию, при 5+ ветках — переполнение

#### Вариант B: Branch Indicator + Dropdown
- Компактный индикатор текущей ветки в хедере чата (иконка + имя)
- По клику — dropdown со списком веток, иерархией и действиями
- Показывает дерево (├─), количество сообщений, точку ветвления
- Плюсы: компактно, показывает иерархию, масштабируется
- Минусы: ветки скрыты за кликом

#### Вариант C: Branch Panel (боковая панель)
- Панель справа (~260px) с деревом веток
- Полная иерархия с отступами
- Превью последних сообщений ветки при наведении
- Плюсы: полная визуализация дерева, превью
- Минусы: занимает экранное пространство

---

## Module Structure

### Новые модули (data layer)
- `modules/data/branch-repository-exposed/` — BranchRepository implementation
- `modules/data/branch-turn-repository-exposed/` — BranchTurnRepository implementation
- `modules/data/active-branch-repository-exposed/` — ActiveBranchRepository implementation

### Изменения в существующих модулях
- `modules/core/` — новые типы (BranchId, Branch), интерфейсы репозиториев, Branching в ContextManagementType
- `modules/domain/context-manager/` — BranchingContextManager (делегат), изменения в DefaultContextManager
- `modules/domain/ai-agent/` — реализация новых методов Agent (createBranch, deleteBranch, switchBranch, etc.)
- `modules/presentation/compose-ui/` — UI компоненты для веток (три варианта навигации)
- `modules/presentation/app/` — Koin DI: регистрация новых репозиториев и BranchingContextManager
- `modules/data/context-management-repository-exposed/` — поддержка сериализации/десериализации `Branching` типа

---

## Testing Strategy

### Unit Tests
- `BranchingContextManager`: collectBranchPath для линейной ветки, ветки от ветки, глубокого дерева
- `BranchingContextManager`: prepareContext формирует корректный PreparedContext
- Каскадное удаление: корректно удаляет дочерние ветки
- Edge cases: ветка без turns, удаление активной ветки → переключение на main

### Repository Tests
- CRUD операции для BranchRepository, BranchTurnRepository, ActiveBranchRepository
- Порядок turns (orderIndex) сохраняется корректно
- Каскадное удаление через deleteByBranch

### Integration Tests
- Полный flow: создать сессию → send в main → создать ветку → send в ветку → проверить контексты обеих веток
- Переключение веток: контекст корректно меняется
- Ветка от ветки: контекст включает полный путь

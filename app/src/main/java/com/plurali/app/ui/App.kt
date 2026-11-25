package com.plurali.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.plurali.app.data.*
import kotlinx.coroutines.launch

fun NavHostController.go(route: String) = navigate(route) { launchSingleTop = true }

private data class DrawerItem(
    val route: String, val label: String, val icon: ImageVector,
    val guest: Boolean = false, val guestOnly: Boolean = false,
)

private val drawerItems = listOf(
    DrawerItem("home", "Início", Icons.Filled.Home, guest = true),
    DrawerItem("welcome", "Boas-vindas", Icons.Filled.WavingHand, guest = true),
    DrawerItem("newcomer", "Sou novo aqui", Icons.Filled.EmojiPeople, guest = true, guestOnly = true),
    DrawerItem("search", "Buscar", Icons.Filled.Search),
    DrawerItem("chats", "Conversas", Icons.Filled.Forum),
    DrawerItem("calendar", "Calendário", Icons.Filled.CalendarMonth, guest = true),
    DrawerItem("reminders", "Lembretes e timers", Icons.Filled.Alarm),
    DrawerItem("body", "Corpo", Icons.Filled.Accessibility, guest = true),
    DrawerItem("map", "Mapa do sistema", Icons.Filled.Hub, guest = true),
    DrawerItem("safety", "Plano de segurança", Icons.Filled.HealthAndSafety, guest = true),
    DrawerItem("history", "Histórico de front", Icons.Filled.BarChart),
    DrawerItem("stats", "Análises", Icons.Filled.Insights),
    DrawerItem("polls", "Enquetes", Icons.Filled.HowToVote),
    DrawerItem("companions", "Companions", Icons.Filled.Share),
    DrawerItem("fields", "Campos personalizados", Icons.Filled.Tune),
    DrawerItem("more", "Sistema e configurações", Icons.Filled.Settings),
    DrawerItem("backup", "Backup", Icons.Filled.Backup),
)

@Composable
fun PluraliApp() {
    val d by Store.data.collectAsState()
    // sem senha do sistema = app aberto; com senha = tela de login
    LaunchedEffect(d.loginsEnabled, d.members) {
        val cur = Session.who
        when {
            !d.loginsEnabled -> Session.who = Who.Open
            cur == Who.Open -> Session.who = null
            // se a pessoa logada foi excluída ou perdeu a senha, volta ao login
            cur is Who.Member && d.members.none { it.id == cur.id && it.password != null } -> Session.who = null
        }
    }
    val w = Session.who
    val effective: Who? = when {
        !d.loginsEnabled -> Who.Open
        w == Who.Open -> null
        else -> w
    }
    var fromLogin by remember { mutableStateOf<String?>(null) } // "safety" | "welcome"
    if (effective == null) {
        when (fromLogin) {
            "safety" -> CompositionLocalProvider(LocalWho provides Who.Guest) { SafetyScreen(null, onClose = { fromLogin = null }) }
            "welcome" -> CompositionLocalProvider(LocalWho provides Who.Guest) { WelcomeScreen(null, onClose = { fromLogin = null }) }
            else -> LoginScreen(d, onSafety = { fromLogin = "safety" }, onWelcome = { fromLogin = "welcome" })
        }
    } else CompositionLocalProvider(LocalWho provides effective) { MainApp(d, effective) }
}

@Composable
private fun MainApp(d: AppData, who: Who) {
    val nav = rememberNavController()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val me = who.meId
    val unread = if (who is Who.Open) 0 else d.chats.filter { who.canSeeChat(it) }.sumOf { d.unreadIn(it, me) }
    LaunchedEffect(Session.pendingRoute) {
        Session.pendingRoute?.let { r ->
            Session.pendingRoute = null
            if (who.canWrite) runCatching { nav.go(r) }
        }
    }
    val items = drawerItems.filter { who.canWrite || it.guest }
        .filter { !it.guestOnly || who is Who.Guest }
        .filter { it.route != "backup" || who.isAdmin }
        .filter { it.route != "chats" || who !is Who.Admin }
    val topLevel = drawerItems.any { it.route == route }

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = topLevel || drawer.isOpen,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        d.systemName.ifBlank { "Plurali" },
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 28.dp, top = 24.dp),
                    )
                    if (who !is Who.Open) Row(
                        Modifier.padding(start = 28.dp, top = 4.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val m = d.members.find { it.id == me }
                        if (m != null) { Avatar(m.name, m.color, m.avatar, 24.dp); Spacer(Modifier.width(8.dp)) }
                        Text("Conectado como ${whoLabel(d, who)}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    } else Spacer(Modifier.height(16.dp))
                    items.forEach { item ->
                        NavigationDrawerItem(
                            label = { Text(item.label) },
                            icon = { Icon(item.icon, null) },
                            selected = route == item.route,
                            badge = { if (item.route == "chats" && unread > 0) Badge { Text("$unread") } },
                            onClick = {
                                scope.launch { drawer.close() }
                                if (route != item.route) nav.navigate(item.route) {
                                    popUpTo("home") { inclusive = item.route == "home" }
                                    launchSingleTop = true
                                }
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                unselectedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                    if (d.loginsEnabled) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 24.dp))
                        NavigationDrawerItem(
                            label = { Text("Trocar de usuário") },
                            icon = { Icon(Icons.AutoMirrored.Filled.Logout, null) },
                            selected = false,
                            onClick = { scope.launch { drawer.close() }; Session.who = null },
                            colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = MaterialTheme.colorScheme.surfaceContainer),
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        },
    ) {
        CompositionLocalProvider(LocalOpenDrawer provides { scope.launch { drawer.open() } }) {
            NavHost(nav, startDestination = "home") {
                composable("home") { HomeScreen(nav) }
                composable("chats") { ChatsScreen(nav) }
                composable("chat/{id}") { ChatScreen(nav, it.arguments?.getString("id") ?: "") }
                composable("chatNew") { NewChatScreen(nav) }
                composable("calendar") { CalendarScreen(nav) }
                composable("eventEdit/{id}/{day}") {
                    EventEditScreen(nav, it.arguments?.getString("id") ?: "new", it.arguments?.getString("day") ?: "")
                }
                composable("body") { BodyScreen(nav) }
                composable("safety") { SafetyScreen(nav, null) }
                composable("welcome") { WelcomeScreen(nav, null) }
                composable("companions") { CompanionsScreen(nav) }
                composable("companionEdit/{id}") { CompanionEditScreen(nav, it.arguments?.getString("id") ?: "new") }
                composable("newcomer") { NewcomerScreen(nav) }
                composable("search") { SearchScreen(nav) }
                composable("reminders") { RemindersScreen(nav) }
                composable("map") { SystemMapScreen(nav) }
                composable("appearance") { AppearanceScreen(nav) }
                composable("notifications") { NotificationsScreen(nav) }
                composable("polls") { PollsScreen(nav) }
                composable("more") { MoreScreen(nav) }
                composable("security") { SecurityScreen(nav) }

                composable("member/{id}") { MemberDetailScreen(nav, it.arguments?.getString("id") ?: "") }
                composable("memberEdit/{id}") { MemberEditScreen(nav, it.arguments?.getString("id") ?: "new") }
                composable("noteEdit/{memberId}/{id}") {
                    NoteEditScreen(nav, it.arguments?.getString("memberId") ?: "", it.arguments?.getString("id") ?: "new")
                }
                composable("groups") { GroupsScreen(nav) }
                composable("groupEdit/{id}") { GroupEditScreen(nav, it.arguments?.getString("id") ?: "new", null) }
                composable("groupNew/{parent}") {
                    GroupEditScreen(nav, "new", it.arguments?.getString("parent")?.takeIf { p -> p != "root" })
                }
                composable("customFronts") { CustomFrontsScreen(nav) }
                composable("customFrontEdit/{id}") { CustomFrontEditScreen(nav, it.arguments?.getString("id") ?: "new") }
                composable("fields") { FieldsScreen(nav) }
                composable("history") { HistoryScreen(nav) }
                composable("stats") { StatsScreen(nav) }
                composable("backup") { BackupScreen(nav) }
                composable("poll/{id}") { PollDetailScreen(nav, it.arguments?.getString("id") ?: "") }
                composable("pollEdit/{id}") { PollEditScreen(nav, it.arguments?.getString("id") ?: "new") }
            }
        }
    }
}

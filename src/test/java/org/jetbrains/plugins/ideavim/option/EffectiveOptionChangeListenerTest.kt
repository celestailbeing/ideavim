/*
 * Copyright 2003-2023 The IdeaVim authors
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE.txt file or at
 * https://opensource.org/licenses/MIT.
 */

package org.jetbrains.plugins.ideavim.option

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.replaceService
import com.intellij.util.childScope
import com.maddyhome.idea.vim.api.VimEditor
import com.maddyhome.idea.vim.api.injector
import com.maddyhome.idea.vim.newapi.ij
import com.maddyhome.idea.vim.newapi.vim
import com.maddyhome.idea.vim.options.EffectiveOptionValueChangeListener
import com.maddyhome.idea.vim.options.OptionDeclaredScope
import com.maddyhome.idea.vim.options.OptionScope
import com.maddyhome.idea.vim.options.StringOption
import com.maddyhome.idea.vim.vimscript.model.datatypes.VimString
import org.jetbrains.plugins.ideavim.SkipNeovimReason
import org.jetbrains.plugins.ideavim.TestWithoutNeovim
import org.jetbrains.plugins.ideavim.VimTestCase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import javax.swing.SwingConstants
import kotlin.test.assertContentEquals

@TestWithoutNeovim(reason = SkipNeovimReason.OPTION)
class EffectiveOptionChangeListenerTest : VimTestCase() {
  private val optionName = "test"
  private lateinit var manager: FileEditorManagerImpl
  private lateinit var otherBufferWindow: Editor
  private lateinit var originalEditor: Editor
  private lateinit var splitWindow: Editor

  private object Listener : EffectiveOptionValueChangeListener {
    val notifiedEditors = mutableListOf<Editor>()

    override fun onEffectiveValueChanged(editor: VimEditor) {
      notifiedEditors.add(editor.ij)
    }
  }

  @BeforeEach
  override fun setUp(testInfo: TestInfo) {
    super.setUp(testInfo)

    // Copied from FileEditorManagerTestCase to allow us to split windows
    @Suppress("DEPRECATION")
    manager = FileEditorManagerImpl(fixture.project, fixture.project.coroutineScope.childScope())
    fixture.project.replaceService(FileEditorManager::class.java, manager, fixture.testRootDisposable)

    // Create a new editor that will represent a new buffer in a separate window. It will have default values
    otherBufferWindow = openNewBufferWindow("bbb.txt")

    // Create the original editor last, so that fixture.editor will point to this file
    // It is STRONGLY RECOMMENDED to use originalEditor instead of fixture.editor, so we know which editor we're using
    originalEditor = configureByText("\n")  // aaa.txt

    // Split the current window. Since no options have been set, it will have default values
    splitWindow = openSplitWindow(originalEditor) // aaa.txt
  }

  override fun createFixture(factory: IdeaTestFixtureFactory): CodeInsightTestFixture {
    val fixture = factory.createFixtureBuilder("IdeaVim").fixture
    return factory.createCodeInsightFixture(fixture)
  }

  // Note that this overwrites fixture.editor! This is the equivalent of `:new {file}`
  private fun openNewBufferWindow(filename: String): Editor {
    fixture.openFileInEditor(fixture.createFile(filename, "lorem ipsum"))
    return fixture.editor
  }

  private fun openSplitWindow(editor: Editor): Editor {
    val fileManager = FileEditorManagerEx.getInstanceEx(fixture.project)
    return (fileManager.currentWindow!!.split(
      SwingConstants.VERTICAL,
      true,
      editor.virtualFile,
      false
    )!!.allComposites.first().selectedEditor as TextEditor).editor
  }

  @AfterEach
  override fun tearDown(testInfo: TestInfo) {
    super.tearDown(testInfo)
    Listener.notifiedEditors.clear()
    injector.optionGroup.removeOption(optionName)
  }

  private fun addOption(scope: OptionDeclaredScope): StringOption {
    val option = StringOption(optionName, scope, optionName, "defaultValue")
    injector.optionGroup.addOption(option)
    injector.optionGroup.addEffectiveOptionValueChangeListener(option, Listener)
    return option
  }

  private fun assertNotifiedEditors(vararg editors: Editor) {
    val sortedExpected = editors.sortedBy { it.virtualFile!!.path }.toTypedArray()
    val sortedActual = Listener.notifiedEditors.sortedBy { it.virtualFile!!.path }.toTypedArray()
    assertContentEquals(sortedExpected, sortedActual)
  }

  @Test
  fun `test listener called for all editors when global option changes`() {
    val option = addOption(OptionDeclaredScope.GLOBAL)
    injector.optionGroup.setOptionValue(option, OptionScope.GLOBAL, VimString("newValue"))

    assertNotifiedEditors(originalEditor, splitWindow, otherBufferWindow)
  }

  @Test
  fun `test listener called for all editors when global option changes at local scope`() {
    val option = addOption(OptionDeclaredScope.GLOBAL)
    injector.optionGroup.setOptionValue(option, OptionScope.LOCAL(fixture.editor.vim), VimString("newValue"))

    assertNotifiedEditors(originalEditor, splitWindow, otherBufferWindow)
  }

  @Test
  fun `test listener called for all editors when global option changes at effective scope`() {
    val option = addOption(OptionDeclaredScope.GLOBAL)
    injector.optionGroup.setOptionValue(option, OptionScope.EFFECTIVE(originalEditor.vim), VimString("newValue"))

    assertNotifiedEditors(originalEditor, splitWindow, otherBufferWindow)
  }

  @Test
  fun `test listener not called when local-to-buffer option changes at global scope`() {
    val option = addOption(OptionDeclaredScope.LOCAL_TO_BUFFER)
    injector.optionGroup.setOptionValue(option, OptionScope.GLOBAL, VimString("newValue"))

    assertNotifiedEditors()
  }

  @Test
  fun `test listener called for all buffer editors when local-to-buffer option changes at local scope`() {
    val option = addOption(OptionDeclaredScope.LOCAL_TO_BUFFER)
    injector.optionGroup.setOptionValue(option, OptionScope.LOCAL(originalEditor.vim), VimString("newValue"))

    assertNotifiedEditors(originalEditor, splitWindow)
  }

  @Test
  fun `test listener called for all buffer editors when local-to-buffer option changes at effective scope`() {
    val option = addOption(OptionDeclaredScope.LOCAL_TO_BUFFER)
    injector.optionGroup.setOptionValue(option, OptionScope.EFFECTIVE(originalEditor.vim), VimString("newValue"))

    assertNotifiedEditors(originalEditor, splitWindow)
  }

  @Test
  fun `test listener not called when local-to-window option changes at global scope`() {
    val option = addOption(OptionDeclaredScope.LOCAL_TO_WINDOW)
    injector.optionGroup.setOptionValue(option, OptionScope.GLOBAL, VimString("newValue"))

    assertNotifiedEditors()
  }

  @Test
  fun `test listener called for single window when local-to-window option changes at local scope`() {
    val option = addOption(OptionDeclaredScope.LOCAL_TO_WINDOW)
    injector.optionGroup.setOptionValue(option, OptionScope.LOCAL(originalEditor.vim), VimString("newValue"))

    assertNotifiedEditors(originalEditor)
  }

  @Test
  fun `test listener called for single window when local-to-window option changes at effective scope`() {
    val option = addOption(OptionDeclaredScope.LOCAL_TO_WINDOW)
    injector.optionGroup.setOptionValue(option, OptionScope.EFFECTIVE(originalEditor.vim), VimString("newValue"))

    assertNotifiedEditors(originalEditor)
  }

  @Test
  fun `test listener called for all editors when unset global-local local-to-buffer option changes at global scope`() {
    val option = addOption(OptionDeclaredScope.GLOBAL_OR_LOCAL_TO_BUFFER)
    injector.optionGroup.setOptionValue(option, OptionScope.GLOBAL, VimString("newValue"))

    assertNotifiedEditors(originalEditor, splitWindow, otherBufferWindow)
  }

  @Test
  fun `test listener called for all buffer editors when unset global-local local-to-buffer option changes at local scope`() {
    val option = addOption(OptionDeclaredScope.GLOBAL_OR_LOCAL_TO_BUFFER)
    injector.optionGroup.setOptionValue(option, OptionScope.LOCAL(originalEditor.vim), VimString("newValue"))

    assertNotifiedEditors(originalEditor, splitWindow)
  }

  @Test
  fun `test listener called for all editors when unset global-local local-to-buffer option changes at effective scope`() {
    val option = addOption(OptionDeclaredScope.GLOBAL_OR_LOCAL_TO_BUFFER)
    injector.optionGroup.setOptionValue(option, OptionScope.EFFECTIVE(originalEditor.vim), VimString("newValue"))

    assertNotifiedEditors(originalEditor, splitWindow, otherBufferWindow)
  }

  @Test
  fun `test listener called for all editors when locally modified global-local local-to-buffer option changes at effective scope`() {
    val option = addOption(OptionDeclaredScope.GLOBAL_OR_LOCAL_TO_BUFFER)
    injector.optionGroup.setOptionValue(option, OptionScope.LOCAL(otherBufferWindow.vim), VimString("localValue"))
    Listener.notifiedEditors.clear()

    injector.optionGroup.setOptionValue(option, OptionScope.EFFECTIVE(otherBufferWindow.vim), VimString("newValue"))

    assertNotifiedEditors(originalEditor, splitWindow, otherBufferWindow)
  }

  @Test
  fun `test listener not called for locally modified editor when global-local local-to-buffer option changes at global scope`() {
    val option = addOption(OptionDeclaredScope.GLOBAL_OR_LOCAL_TO_BUFFER)
    injector.optionGroup.setOptionValue(option, OptionScope.LOCAL(otherBufferWindow.vim), VimString("localValue"))
    Listener.notifiedEditors.clear()

    injector.optionGroup.setOptionValue(option, OptionScope.GLOBAL, VimString("newValue"))

    assertNotifiedEditors(originalEditor, splitWindow)
  }

  @Test
  fun `test listener called for all editors when unset global-local local-to-window option changes at global scope`() {
    val option = addOption(OptionDeclaredScope.GLOBAL_OR_LOCAL_TO_WINDOW)
    injector.optionGroup.setOptionValue(option, OptionScope.GLOBAL, VimString("newValue"))

    assertNotifiedEditors(originalEditor, splitWindow, otherBufferWindow)
  }

  @Test
  fun `test listener called for single editor when unset global-local local-to-window option changes at local scope`() {
    val option = addOption(OptionDeclaredScope.GLOBAL_OR_LOCAL_TO_WINDOW)
    injector.optionGroup.setOptionValue(option, OptionScope.LOCAL(originalEditor.vim), VimString("newValue"))

    assertNotifiedEditors(originalEditor)
  }

  @Test
  fun `test listener called for all editors when unset global-local local-to-window option changes at effective scope`() {
    val option = addOption(OptionDeclaredScope.GLOBAL_OR_LOCAL_TO_WINDOW)
    injector.optionGroup.setOptionValue(option, OptionScope.EFFECTIVE(originalEditor.vim), VimString("newValue"))

    assertNotifiedEditors(originalEditor, splitWindow, otherBufferWindow)
  }

  @Test
  fun `test listener called for all editors when locally modified global-local local-to-window option changes at effective scope`() {
    val option = addOption(OptionDeclaredScope.GLOBAL_OR_LOCAL_TO_WINDOW)
    injector.optionGroup.setOptionValue(option, OptionScope.LOCAL(originalEditor.vim), VimString("localValue"))
    Listener.notifiedEditors.clear()

    injector.optionGroup.setOptionValue(option, OptionScope.EFFECTIVE(originalEditor.vim), VimString("newValue"))

    assertNotifiedEditors(originalEditor, splitWindow, otherBufferWindow)
  }

  @Test
  fun `test listener not called for locally modified editor when global-local local-to-window option changes at global scope`() {
    val option = addOption(OptionDeclaredScope.GLOBAL_OR_LOCAL_TO_WINDOW)
    injector.optionGroup.setOptionValue(option, OptionScope.LOCAL(otherBufferWindow.vim), VimString("localValue"))
    Listener.notifiedEditors.clear()

    injector.optionGroup.setOptionValue(option, OptionScope.GLOBAL, VimString("newValue"))

    assertNotifiedEditors(originalEditor, splitWindow)
  }
}
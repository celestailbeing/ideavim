/*
 * IdeaVim - Vim emulator for IDEs based on the IntelliJ platform
 * Copyright (C) 2003-2022 The IdeaVim authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.maddyhome.idea.vim.action.motion.text

import com.maddyhome.idea.vim.VimPlugin
import com.maddyhome.idea.vim.command.Argument
import com.maddyhome.idea.vim.command.CommandFlags
import com.maddyhome.idea.vim.command.MotionType
import com.maddyhome.idea.vim.command.OperatorArguments
import com.maddyhome.idea.vim.handler.Motion
import com.maddyhome.idea.vim.handler.MotionActionHandler
import com.maddyhome.idea.vim.handler.toMotionOrError
import com.maddyhome.idea.vim.helper.enumSetOf
import com.maddyhome.idea.vim.newapi.ExecutionContext
import com.maddyhome.idea.vim.newapi.VimCaret
import com.maddyhome.idea.vim.newapi.VimEditor
import com.maddyhome.idea.vim.newapi.ij
import java.util.*

sealed class MotionUnmatchedAction(private val motionChar: Char) : MotionActionHandler.ForEachCaret() {
  override val flags: EnumSet<CommandFlags> = enumSetOf(CommandFlags.FLAG_SAVE_JUMP)

  override val motionType: MotionType = MotionType.EXCLUSIVE

  override fun getOffset(
    editor: VimEditor,
    caret: VimCaret,
    context: ExecutionContext,
    argument: Argument?,
    operatorArguments: OperatorArguments,
  ): Motion {
    return VimPlugin.getMotion()
      .moveCaretToUnmatchedBlock(editor.ij, caret.ij, operatorArguments.count1, motionChar)
      .toMotionOrError()
  }
}

class MotionUnmatchedBraceCloseAction : MotionUnmatchedAction('}')
class MotionUnmatchedBraceOpenAction : MotionUnmatchedAction('{')
class MotionUnmatchedParenCloseAction : MotionUnmatchedAction(')')
class MotionUnmatchedParenOpenAction : MotionUnmatchedAction('(')
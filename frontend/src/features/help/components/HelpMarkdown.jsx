import { Fragment } from 'react';
import { parseInline } from '@/features/help/lib/markdownBlocks';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';

function InlineText({ text }) {
  return parseInline(text).map((token, index) => {
    if (token.type === 'strong') {
      return (
        <strong key={index} className="font-semibold text-ink">
          {token.value}
        </strong>
      );
    }
    if (token.type === 'code') {
      return (
        <code key={index} className="rounded-control bg-neutral-bg px-1 text-size-13">
          {token.value}
        </code>
      );
    }
    if (token.type === 'link') {
      const external = /^https?:/i.test(token.href);
      return (
        <a
          key={index}
          href={token.href}
          className="text-primary underline underline-offset-2"
          {...(external ? { target: '_blank', rel: 'noopener noreferrer' } : {})}
        >
          {token.value}
        </a>
      );
    }
    return <Fragment key={index}>{token.value}</Fragment>;
  });
}

function HeadingBlock({ block }) {
  const Tag = block.level === 2 ? 'h3' : 'h4';
  return (
    <Tag id={block.id} className="scroll-mt-24 pt-2 font-heading text-size-16 font-semibold text-ink">
      {block.text}
    </Tag>
  );
}

function ListBlock({ block }) {
  const ordered = block.type === 'ordered';
  const Tag = ordered ? 'ol' : 'ul';
  return (
    <Tag className={`${ordered ? 'list-decimal' : 'list-disc'} flex flex-col gap-1 pl-5 text-sm leading-relaxed text-ink-soft`}>
      {block.items.map((item, index) => (
        <li key={index}>
          <InlineText text={item} />
        </li>
      ))}
    </Tag>
  );
}

function TableBlock({ block }) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          {block.header.map((cell, index) => (
            <TableHead key={index}>
              <InlineText text={cell} />
            </TableHead>
          ))}
        </TableRow>
      </TableHeader>
      <TableBody>
        {block.rows.map((row, rowIndex) => (
          <TableRow key={rowIndex}>
            {row.map((cell, index) => (
              <TableCell key={index} className="whitespace-normal">
                <InlineText text={cell} />
              </TableCell>
            ))}
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

function MarkdownBlock({ block }) {
  if (block.type === 'heading') return <HeadingBlock block={block} />;
  if (block.type === 'paragraph') {
    return (
      <p className="text-sm leading-relaxed text-ink-soft">
        <InlineText text={block.text} />
      </p>
    );
  }
  if (block.type === 'table') return <TableBlock block={block} />;
  return <ListBlock block={block} />;
}

export function HelpMarkdown({ blocks }) {
  return (
    <div className="flex flex-col gap-3">
      {blocks.map((block, index) => (
        <MarkdownBlock key={index} block={block} />
      ))}
    </div>
  );
}
